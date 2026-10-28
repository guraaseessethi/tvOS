package app.lumen.watcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.lumen.Lumen
import app.lumen.MainActivity
import app.lumen.R
import app.lumen.auth.Auth
import app.lumen.bridge.BridgeServer
import app.lumen.bridge.Memories
import app.lumen.ecp.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * Spec 15.4 + PLAN section 7: one foreground service that
 *  - hosts the Lumen bridge on :8977 and tells Lumen where it is,
 *  - opens Lumen when the TV turns on onto Roku's home,
 *  - reopens Lumen when you back out of an app onto Roku's home (Home pressed in Lumen stays on Roku home),
 *  - runs the sleep timer, and follows the weather for the painting.
 */
class TvWatcherService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: BridgeServer? = null
    private var lastNotice = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Lumen.init(this)
        channels()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, NOTE_ID, notification("Watching for your TV"), type)
        server = BridgeServer(this, BridgeServer.PORT, Memories(this)).also {
            runCatching { it.start(5000, false) }
        }
        scope.launch { watch() }
        scope.launch { housekeeping() }
        scope.launch { weather() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        server?.stop()
        scope.cancel()
        super.onDestroy()
    }

    // ---- the watcher (port of bridge/lumen_bridge.py watcher()) ----

    private suspend fun watch() {
        var announced = false
        var announcedAt = 0L
        var last: Screen? = null
        var stayHome = false
        var wasOn: Boolean? = null
        var bootStart = 0L
        var bootUntil = 0L
        var tick = 0
        var misses = 0
        var appsAt = 0L
        while (scope.isActive) {
            val s = Lumen.loadedSettings()
            val ip = s.tvIp
            if (ip.isEmpty()) { delay(3000); continue }
            val ecp = Lumen.ecp
            val auto = s.autoOpen && System.currentTimeMillis() > s.pauseUntil

            if (tick % 3 == 0) {
                val info = ecp.deviceInfo(ip)
                val on = info?.isOn == true
                if (info != null) {
                    misses = 0
                    if (on && wasOn == false) {
                        bootStart = System.currentTimeMillis()
                        bootUntil = bootStart + 25_000
                        stayHome = false
                        last = null
                    }
                    wasOn = on
                    Lumen.tv.value = Lumen.tv.value.copy(reachable = true, info = info, checkedAt = System.currentTimeMillis())
                } else {
                    misses++
                    if (wasOn == true) wasOn = false  // unreachable counts as off, so the next answer is a power-on
                    if (misses >= 3) Lumen.tv.value = Lumen.tv.value.copy(reachable = false, screen = null)
                }
            }
            tick++

            val now = if (misses >= 3) null else ecp.activeScreen(ip)
            if (now != null && Lumen.tv.value.screen != now) Lumen.tv.value = Lumen.tv.value.copy(screen = now)
            if (now == Screen.Lumen && (!announced || System.currentTimeMillis() - announcedAt > 60_000)) {
                announced = announce(ip)
                announcedAt = System.currentTimeMillis()
            } else if (now != Screen.Lumen) {
                announced = false
            }

            if (System.currentTimeMillis() < bootUntil && auto) {
                // Boot window: open Lumen the moment Roku's home shows; stand down on anything else.
                if (now == Screen.Home) {
                    if (ecp.launch(ip, "dev")) {
                        val secs = (System.currentTimeMillis() - bootStart) / 1000.0
                        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
                        Lumen.prefs.setLastAutoOpen("today at $time, ${"%.1f".format(secs)} s after the TV turned on|${dayStamp()}")
                        bootUntil = 0
                    }
                } else if (now == Screen.Lumen || now == Screen.Other) {
                    bootUntil = 0
                }
            } else if (now == Screen.Other) {
                stayHome = false
            } else if (now == Screen.Home && last != Screen.Home && auto) {
                if (last == Screen.Lumen) {
                    stayHome = true   // Home pressed in Lumen: leave Roku's home alone until another app opens
                } else if (!stayHome) {
                    ecp.launch(ip, "dev")
                }
            }
            if (now != null) last = now

            if (System.currentTimeMillis() - appsAt > 60_000 && misses == 0) {
                appsAt = System.currentTimeMillis()
                Lumen.refreshTv(withApps = true)
            }
            updateNotice()
            delay(
                when {
                    misses >= 3 -> 5000L
                    wasOn == false -> 1500L
                    else -> 400L
                },
            )
        }
    }

    /** POST /input?bridge=<url>&phone=<status>[&theme=...] so Lumen knows where its helper is. */
    private suspend fun announce(ip: String): Boolean {
        val url = "http://${ownIp(ip) ?: return false}:${BridgeServer.PORT}"
        val name = Auth.current(this)?.name ?: ""
        val params = mutableMapOf(
            "bridge" to url,
            "phone" to JSONObject().put("connected", true).put("name", name).put("code", "").toString(),
        )
        val s = Lumen.settings.value
        if (s.followWeather || s.overrideUntil > System.currentTimeMillis()) {
            params["theme"] = JSONObject().put("theme", Lumen.painting.value.key).toString()
        }
        return Lumen.ecp.input(ip, params)
    }

    private fun ownIp(tv: String): String? = runCatching {
        DatagramSocket().use { s ->
            s.connect(InetSocketAddress(tv, 8060))
            s.localAddress.hostAddress
        }
    }.getOrNull()

    // ---- sleep timer + notification ----

    private suspend fun housekeeping() {
        var warned = 0L
        while (scope.isActive) {
            val s = Lumen.loadedSettings()
            val at = s.sleepAt
            if (at > 0) {
                val left = at - System.currentTimeMillis()
                if (left <= 0) {
                    Lumen.ip()?.let { Lumen.ecp.keypress(it, "PowerOff") }
                    Lumen.prefs.setSleepAt(0)
                    nm().cancel(SLEEP_NOTE_ID)
                } else if (left < 60_000 && warned != at) {
                    warned = at
                    nm().notify(SLEEP_NOTE_ID, NotificationCompat.Builder(this, CH_TIMER)
                        .setSmallIcon(R.drawable.ic_stat_zeus)
                        .setContentTitle("TV turns off in 1 minute")
                        .setContentText("Sleep timer from Zeus")
                        .addAction(0, "Cancel timer", action(NotificationActions.CANCEL_SLEEP))
                        .setAutoCancel(true)
                        .build())
                }
            }
            delay(1000)
        }
    }

    private suspend fun weather() {
        var shown = Lumen.painting.value
        while (scope.isActive) {
            Lumen.refreshWeather()
            delay(500)
            val p = Lumen.painting.value
            if (p != shown) {
                shown = p
                Lumen.sendTheme(p)
            }
            LumenWidget.refresh(this@TvWatcherService)
            delay(30 * 60_000L)
        }
    }

    private fun updateNotice() {
        val tv = Lumen.tv.value
        val s = Lumen.settings.value
        val name = s.tvName.ifEmpty { "your TV" }
        val paused = s.pauseUntil > System.currentTimeMillis()
        val text = when {
            !tv.reachable -> "Looking for $name"
            tv.isOn && tv.screen == Screen.Lumen -> "TV on · Lumen open"
            tv.isOn -> "TV on"
            paused -> "Auto-open paused · watching $name"
            else -> "Watching for $name"
        }
        val key = "$text|${s.lockRemote}|$paused"
        if (key == lastNotice) return
        lastNotice = key
        nm().notify(NOTE_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val s = Lumen.settings.value
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val talk = PendingIntent.getActivity(this, 1,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_LISTEN, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val paused = s.pauseUntil > System.currentTimeMillis()
        return NotificationCompat.Builder(this, CH_WATCH)
            .setSmallIcon(R.drawable.ic_stat_zeus)
            .setContentTitle("Lumen")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setColor(0xFFFFDE03.toInt())
            .setVisibility(if (s.lockRemote) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_SECRET)
            .addAction(0, "Talk to Zeus", talk)
            .addAction(0, "Play/Pause", action(NotificationActions.PLAY))
            .addAction(0, if (paused) "Resume auto-open" else "Pause auto-open",
                action(if (paused) NotificationActions.RESUME_AUTO else NotificationActions.PAUSE_AUTO))
            .build()
    }

    private fun action(what: String): PendingIntent = PendingIntent.getBroadcast(
        this, what.hashCode(), Intent(this, NotificationActions::class.java).setAction(what), PendingIntent.FLAG_IMMUTABLE,
    )

    private fun nm() = getSystemService(NotificationManager::class.java)

    private fun channels() {
        if (Build.VERSION.SDK_INT < 26) return
        nm().createNotificationChannel(NotificationChannel(CH_WATCH, "TV watcher", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Keeps Lumen ready to open when the TV turns on"
            setShowBadge(false)
        })
        nm().createNotificationChannel(NotificationChannel(CH_TIMER, "Sleep timer", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun dayStamp(): String {
        val c = Calendar.getInstance()
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
    }

    companion object {
        const val NOTE_ID = 1
        const val SLEEP_NOTE_ID = 2
        const val CH_WATCH = "watch"
        const val CH_TIMER = "timer"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, TvWatcherService::class.java))
        }
    }
}
