package app.lumen.bridge

import android.content.Context
import app.lumen.Lumen
import app.lumen.games.Game
import app.lumen.games.Leaderboard
import app.lumen.zeus.Turn
import app.lumen.zeus.ZeusBrain
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import kotlinx.coroutines.runBlocking
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

    private suspend fun zeus(body: JSONObject): JSONObject {
        val ctx = body.optJSONObject("context") ?: JSONObject()
        val hist = ctx.optJSONArray("history")
        val turns = (0 until (hist?.length() ?: 0)).mapNotNull { i ->
            hist!!.optJSONObject(i)?.let { Turn(it.optString("role") == "zeus", it.optString("text")) }
        }
        val apps = Lumen.tv.value.apps.ifEmpty { Lumen.refreshTv().apps }
        val facts = JSONObject()
            .put("source", "tv")
            .put("localTime", ctx.optString("localTime"))
            .put("painting", ctx.optString("theme"))
            .put("sleepTimerMinutesLeft", Lumen.sleepMinutesLeft())
        Lumen.weather.value?.let { facts.put("weather", it.line(Lumen.settings.value.fahrenheit)) }
        return ZeusBrain.ask(context, body.optString("message"), apps, turns, facts).toJson()
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
