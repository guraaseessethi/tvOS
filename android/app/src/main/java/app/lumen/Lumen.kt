package app.lumen

import android.content.Context
import android.os.Build
import app.lumen.data.Prefs
import app.lumen.data.Settings
import app.lumen.ecp.DeviceInfo
import app.lumen.ecp.EcpClient
import app.lumen.ecp.EcpParse
import app.lumen.ecp.RokuApp
import app.lumen.ecp.Screen
import app.lumen.theme.Painting
import app.lumen.theme.Weather
import app.lumen.theme.WeatherRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

/** What the phone currently knows about the TV. */
data class TvState(
    val reachable: Boolean = false,
    val info: DeviceInfo? = null,
    val screen: Screen? = null,
    val apps: List<RokuApp> = emptyList(),
    /** false when /query/active-app answered 403: "Control by mobile apps" is off. */
    val mobileControl: Boolean? = null,
    /** Whether `dev` (the sideloaded Lumen channel) is in /query/apps; null until checked. */
    val lumenInstalled: Boolean? = null,
    val checkedAt: Long = 0,
) {
    val isOn get() = info?.isOn == true
    val realApps get() = apps.filter { !it.isInput }
    val inputs get() = apps.filter { it.isInput }
    val status: String
        get() = when {
            !reachable -> "Can't see the TV"
            !isOn -> "Off"
            screen == Screen.Lumen -> "On · Lumen open"
            screen == Screen.Home -> "On · Roku home"
            else -> "On"
        }
}

/** App-wide state and the actions every screen and the service share. */
object Lumen {
    lateinit var context: Context
        private set
    lateinit var prefs: Prefs
        private set
    val ecp = EcpClient()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val weatherRepo = WeatherRepo()

    lateinit var settings: StateFlow<Settings>
        private set
    val tv = MutableStateFlow(TvState())
    val weather = MutableStateFlow<Weather?>(null)
    lateinit var painting: StateFlow<Painting>
        private set

    /** The phone's own name, sent to the TV in hellos (spec 15.6). */
    val phoneName: String = Build.MODEL ?: "Phone"

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        context = ctx.applicationContext
        prefs = Prefs(context)
        settings = prefs.settings.stateIn(scope, SharingStarted.Eagerly, Settings())
        painting = combine(settings, weather) { s, w -> paintingFor(s, w) }
            .stateIn(scope, SharingStarted.Eagerly, Painting.Starry)
        app.lumen.zeus.VoiceStudio.init(context)
    }

    /** This phone's address as the TV sees it, e.g. http://192.168.2.40:8977 (PLAN 7). */
    fun bridgeUrl(tvIp: String): String? = runCatching {
        java.net.DatagramSocket().use { s ->
            s.connect(java.net.InetSocketAddress(tvIp, 8060))
            "http://${s.localAddress.hostAddress}:${app.lumen.bridge.BridgeServer.PORT}"
        }
    }.getOrNull()

    fun paintingFor(s: Settings, w: Weather?): Painting = when {
        s.overrideKey.isNotEmpty() && s.overrideUntil > System.currentTimeMillis() -> Painting.of(s.overrideKey)
        s.followWeather && w != null -> w.painting
        else -> Painting.of(s.themeKey)
    }

    /** Settings, waiting for the first real read from disk. */
    suspend fun loadedSettings(): Settings = prefs.settings.first()

    // ---- TV state ----

    suspend fun refreshTv(withApps: Boolean = true): TvState {
        val ip = loadedSettings().tvIp
        if (ip.isEmpty()) return TvState().also { tv.value = it }
        val info = ecp.deviceInfo(ip)
        if (info == null) {
            tv.value = tv.value.copy(reachable = false, checkedAt = System.currentTimeMillis())
            return tv.value
        }
        val screen = ecp.activeScreen(ip)
        val code = ecp.lastActiveCode
        var apps = tv.value.apps
        var lumen = tv.value.lumenInstalled
        if (withApps || apps.isEmpty()) {
            val r = ecp.call(ip, "GET", "/query/apps")
            if (r.ok) {
                apps = EcpParse.parseApps(r.text)
                lumen = r.text.contains("id=\"dev\"")
            }
        }
        val next = TvState(
            reachable = true, info = info, screen = screen, apps = apps,
            mobileControl = when (code) { 200 -> true; 401, 403 -> false; else -> tv.value.mobileControl },
            lumenInstalled = lumen,
            checkedAt = System.currentTimeMillis(),
        )
        tv.value = next
        return next
    }

    fun key(k: String) = scope.launch { ip()?.let { ecp.keypress(it, k) } }

    fun keyRepeat(k: String, times: Int) = scope.launch {
        val ip = ip() ?: return@launch
        repeat(times.coerceIn(1, 10)) { ecp.keypress(ip, k); delay(120) }
    }

    suspend fun launch(id: String): Boolean {
        val ip = ip() ?: return false
        return ecp.launch(ip, id)
    }

    suspend fun ip(): String? = loadedSettings().tvIp.takeIf { it.isNotEmpty() }

    // ---- painting / weather ----

    suspend fun refreshWeather() {
        var s = loadedSettings()
        if (s.lat.isNaN()) {
            val ll = weatherRepo.geocode(s.city) ?: return
            prefs.setCity(s.city, ll.first, ll.second)
            s = loadedSettings()
        }
        weatherRepo.current(s.lat, s.lon, s.city)?.let { weather.value = it }
    }

    suspend fun setCity(city: String): Boolean {
        val ll = weatherRepo.geocode(city) ?: return false
        prefs.setCity(city, ll.first, ll.second)
        refreshWeather()
        return true
    }

    /** Repaints the TV (spec 15.6 theme sync). until = 0 means "until the weather changes" (6 h). */
    suspend fun repaint(p: Painting, until: Long = 0) {
        val end = if (until > 0) until else System.currentTimeMillis() + 6 * 3600_000L
        prefs.setOverride(p.key, end)
        prefs.setTheme(p.key)
        sendTheme(p)
        app.lumen.watcher.LumenWidget.refresh(context)
    }

    suspend fun clearOverride() {
        prefs.setOverride("", 0)
        sendTheme(painting.value)
    }

    suspend fun sendTheme(p: Painting) {
        val ip = ip() ?: return
        val json = JSONObject().put("theme", p.key).toString()
        if (tv.value.screen == Screen.Lumen) ecp.input(ip, mapOf("theme" to json))
    }

    // ---- sleep timer (runs in TvWatcherService) ----

    /**
     * The sleep timer always lives on the phone, whoever set it (spec 15.6 timer handoff): the TV's
     * sleep sheet and its Zeus reach it through the bridge's POST /sleep. The TV is told so its
     * sheet shows the current timer.
     */
    suspend fun setSleep(minutes: Int) {
        prefs.setSleepAt(if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0)
        ip()?.let { ecp.input(it, mapOf("sleep" to minutes.toString())) }
        app.lumen.watcher.Timers.pushToTv()
        app.lumen.watcher.LumenWidget.refresh(context)
    }

    fun sleepMinutesLeft(): Int {
        val at = settings.value.sleepAt
        if (at <= 0) return 0
        return ((at - System.currentTimeMillis()) / 60_000.0).let { Math.round(it).toInt() }.coerceAtLeast(0)
    }
}
