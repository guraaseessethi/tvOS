package app.lumen.bridge

import android.content.Context
import app.lumen.Lumen
import app.lumen.games.Game
import app.lumen.games.Leaderboard
import app.lumen.ecp.RokuApp
import app.lumen.watcher.Timers
import app.lumen.zeus.Action
import app.lumen.zeus.ActionRunner
import app.lumen.zeus.Answer
import app.lumen.zeus.LocalIntents
import app.lumen.zeus.VoiceStudio
import app.lumen.zeus.ZeusBrain
import app.lumen.zeus.ZeusLog
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URLDecoder

/**
 * The Lumen bridge, hosted on the phone (PLAN section 7). Roku OS 16 blocks ECP from a channel to
 * its own TV, so Lumen asks this server instead. Same contract as bridge/lumen_bridge.py, plus
 * POST /score and GET /leaderboard/<game> for the games card.
 */
class BridgeServer(private val context: Context, port: Int, private val memories: Memories) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response = try {
        runBlocking { route(session) }
    } catch (e: Exception) {
        reply(500, "text/plain", ByteArray(0))
    }

    private suspend fun route(s: IHTTPSession): Response {
        val path = s.uri
        val query = s.queryParameterString.orEmpty()
        val ip = Lumen.ip() ?: return reply(503, "text/plain", "no TV".toByteArray())
        val ecp = Lumen.ecp
        if (s.method == Method.GET) {
            when {
                path == "/ping" -> return text("lumen-bridge")
                path in setOf("/query/apps", "/query/device-info", "/query/active-app") ->
                    return ecp.call(ip, "GET", path).let { reply(it.code, it.type, it.body) }
                path.startsWith("/query/icon/") -> {
                    val id = path.removePrefix("/query/icon/")
                    if (!APP_ID.matches(id)) return reply(404)
                    val r = ecp.call(ip, "GET", path)
                    if (!r.ok || query.contains("raw=1")) return reply(r.code, r.type, r.body)
                    return IconKeyer.cached(context.cacheDir, id, r.body)?.let { reply(200, "image/png", it) } ?: reply(r.code, r.type, r.body)
                }
                path == "/memories" -> {
                    val names = JSONArray(memories.list().map { it.name })
                    return json(JSONObject().put("photos", names))
                }
                path.startsWith("/memories/") -> {
                    val f = memories.file(URLDecoder.decode(path.removePrefix("/memories/"), "UTF-8")) ?: return reply(404)
                    return if (query.contains("card=1")) memories.card(f)?.let { reply(200, "image/png", it) } ?: reply(500)
                    else reply(200, "image/jpeg", f.readBytes())
                }
                path == "/sleep" -> return json(JSONObject().put("minutes", Lumen.sleepMinutesLeft()))
                path == "/timers" -> return json(Timers.statusJson())
                path.startsWith("/voice/") -> {
                    val f = VoiceStudio.file(path.removePrefix("/voice/")) ?: return reply(404)
                    return reply(200, if (f.name.endsWith(".m4a")) "audio/mp4" else "audio/wav", f.readBytes())
                }
                path.startsWith("/leaderboard/") -> {
                    val game = Game.of(path.removePrefix("/leaderboard/")) ?: return reply(404)
                    val top = Leaderboard.topOnce(context, game, 5)
                    return json(JSONObject().put("entries", JSONArray(top.map { JSONObject().put("name", it.name).put("score", it.score) })))
                }
            }
            return reply(404)
        }
        if (s.method == Method.POST) {
            when {
                path.startsWith("/launch/") -> {
                    val id = path.removePrefix("/launch/")
                    if (!APP_ID.matches(id)) return reply(404)
                    return reply(ecp.call(ip, "POST", path).code)
                }
                path.startsWith("/keypress/") -> {
                    if (path.removePrefix("/keypress/") !in KEYS) return reply(404)
                    return reply(ecp.call(ip, "POST", path).code)
                }
                path == "/search" -> {
                    val b = body(s)
                    val q = b.optString("query").trim().take(80)
                    if (q.isEmpty()) return reply(400)
                    val app = b.optString("appId").takeIf { APP_ID.matches(it) && it != "dev" }
                    return reply(if (ecp.search(ip, q, app)) 200 else 502)
                }
                path.startsWith("/sleep/") -> {
                    val m = path.removePrefix("/sleep/").toIntOrNull() ?: return reply(404)
                    if (m !in 0..240) return reply(404)
                    Lumen.setSleep(m)
                    return reply(200)
                }
                path == "/zeus" -> return json(zeus(body(s)))
                path == "/score" -> return json(score(body(s)))
            }
        }
        return reply(404)
    }

    /** The TV's Zeus panel asks here. Same brain and same conversation as the phone (ZeusLog). */
    private suspend fun zeus(body: JSONObject): JSONObject {
        val ctx = body.optJSONObject("context") ?: JSONObject()
        val message = body.optString("message")
        val apps = Lumen.tv.value.apps.ifEmpty { Lumen.refreshTv().apps }
        val facts = JSONObject()
            .put("source", "tv")
            .put("localTime", ctx.optString("localTime"))
            .put("painting", ctx.optString("theme"))
            .put("sleepTimerMinutesLeft", Lumen.sleepMinutesLeft())
        Lumen.weather.value?.let { facts.put("weather", it.line(Lumen.settings.value.fahrenheit)) }
        val answer = LocalIntents.match(message, apps, Lumen.settings.value.inputNames)
            ?: withTimeoutOrNull(25_000) { ZeusBrain.ask(context, message, apps, ZeusLog.history(), facts) }
            ?: Answer("That took too long. Try again?", chips = listOf("Try again"))
        // The TV runs the actions itself; the phone keeps the conversation and shows it.
        // Timers and alarms always live on the phone, so run those here; the TV runs everything else.
        val (onPhone, onTv) = answer.actions.partition { it.type in setOf("set_timer", "cancel_timer", "set_alarm", "cancel_alarm") }
        ActionRunner.run(onPhone)
        val answerForTv = answer.copy(actions = onTv)
        ZeusLog.add(ZeusLog.Exchange(message, answer.reply, answer.actions.map { describe(it, apps) }, fromTv = true))
        // Zeus answers out loud on the TV, in the phone's natural voice.
        val out = answerForTv.toJson()
        if (Lumen.settings.value.spokenReplies) {
            val clip = withTimeoutOrNull(5_000) { VoiceStudio.render(answer.reply, Lumen.settings.value.voiceName) }
            val base = Lumen.ip()?.let { Lumen.bridgeUrl(it) }
            if (clip != null && base != null) out.put("voice", "$base/voice/$clip")
        }
        return out
    }

    private fun describe(a: Action, apps: List<RokuApp>): String = when (a.type) {
        "launch_app" -> (apps.firstOrNull { it.id == a.appId }?.name ?: "App") + " opened on TV"
        "switch_input" -> "Switched input"
        "set_theme" -> "Repainted"
        "set_sleep_timer" -> "Sleep timer ${a.minutes} min"
        "cancel_sleep_timer" -> "Sleep timer off"
        "power" -> if (a.state == "off") "TV turned off" else "TV turning on"
        "keypress" -> a.key ?: "Button"
        "search" -> "Searched for ${a.query}"
        "set_timer" -> "Timer: " + app.lumen.zeus.TimeRules.describe(a.seconds ?: 0)
        "set_alarm" -> "Alarm " + (a.time ?: "")
        else -> a.type
    }

    private suspend fun score(body: JSONObject): JSONObject {
        val game = Game.of(body.optString("game")) ?: return JSONObject().put("ok", false)
        val points = body.optInt("points", 0)
        val r = Leaderboard.submit(context, game, points) ?: return JSONObject().put("ok", false)
        return JSONObject().put("ok", true).put("best", r.best).put("rank", r.rank).put("newBest", r.newBest)
    }

    private fun body(s: IHTTPSession): JSONObject {
        val n = (s.headers["content-length"]?.toIntOrNull() ?: 0).coerceIn(0, 16384)
        val buf = ByteArray(n)
        var read = 0
        while (read < n) {
            val k = s.inputStream.read(buf, read, n - read)
            if (k <= 0) break
            read += k
        }
        return runCatching { JSONObject(String(buf, 0, read, Charsets.UTF_8)) }.getOrDefault(JSONObject())
    }

    private fun text(t: String) = reply(200, "text/plain", t.toByteArray())
    private fun json(o: JSONObject) = reply(200, "application/json", o.toString().toByteArray())

    private fun reply(code: Int, type: String = "text/plain", body: ByteArray = ByteArray(0)): Response {
        val reason = when (code) { 200 -> "OK"; 404 -> "Not Found"; 403 -> "Forbidden"; 500 -> "Internal Server Error"; else -> "Relay" }
        val status = object : Response.IStatus {
            override fun getDescription() = "$code $reason"
            override fun getRequestStatus() = code
        }
        return newFixedLengthResponse(status, type, ByteArrayInputStream(body), body.size.toLong())
    }

    companion object {
        const val PORT = 8977
        val KEYS = setOf(
            "Home", "Back", "Select", "Up", "Down", "Left", "Right", "Info", "Play", "Rev", "Fwd",
            "InstantReplay", "VolumeUp", "VolumeDown", "VolumeMute", "PowerOff", "PowerOn", "Backspace",
        )
        val APP_ID = Regex("^(?:\\d{1,10}|tvinput\\.[a-z0-9]{1,20}|dev)$")
    }
}
