package app.lumen.ecp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import app.lumen.ecp.EcpParse.query
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Roku External Control Protocol over the LAN, http://<tv>:8060 (spec 15.3). */
class EcpClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    class Reply(val code: Int, val type: String, val body: ByteArray) {
        val ok get() = code in 200..299
        val text get() = String(body, Charsets.UTF_8)
    }

    /** Raw call; code 502 means the TV didn't answer. Never throws. */
    suspend fun call(ip: String, method: String, path: String): Reply = withContext(Dispatchers.IO) {
        try {
            val b = Request.Builder().url("http://$ip:8060$path")
            if (method == "POST") b.post(ByteArray(0).toRequestBody(null)) else b.get()
            http.newCall(b.build()).execute().use { r ->
                Reply(r.code, r.header("Content-Type") ?: "application/octet-stream", r.body?.bytes() ?: ByteArray(0))
            }
        } catch (e: IOException) {
            Reply(502, "text/plain", ByteArray(0))
        } catch (e: IllegalArgumentException) {
            Reply(400, "text/plain", ByteArray(0))
        }
    }

    suspend fun deviceInfo(ip: String): DeviceInfo? {
        val r = call(ip, "GET", "/query/device-info")
        if (!r.ok) return null
        val m = EcpParse.flat(r.text)
        return DeviceInfo(
            name = m["user-device-name"]?.takeIf { it.isNotBlank() } ?: m["friendly-device-name"] ?: m["model-name"] ?: "Roku TV",
            model = m["model-name"] ?: "",
            serial = m["serial-number"] ?: "",
            powerMode = m["power-mode"] ?: "",
            isTv = m["is-tv"] == "true",
        )
    }

    /** Active app, or null if the TV didn't answer. Also returns the HTTP code via [lastActiveCode]. */
    suspend fun activeScreen(ip: String): Screen? {
        val r = call(ip, "GET", "/query/active-app")
        lastActiveCode = r.code
        if (!r.ok) return null
        return EcpParse.screenOf(r.text)
    }

    @Volatile var lastActiveCode = 0
        private set

    suspend fun apps(ip: String): List<RokuApp>? {
        val r = call(ip, "GET", "/query/apps")
        if (!r.ok) return null
        return EcpParse.parseApps(r.text)
    }

    suspend fun launch(ip: String, id: String, params: Map<String, String> = emptyMap()) =
        call(ip, "POST", "/launch/$id" + query(params)).ok

    suspend fun keypress(ip: String, key: String) = call(ip, "POST", "/keypress/$key").ok
    suspend fun keydown(ip: String, key: String) = call(ip, "POST", "/keydown/$key").ok
    suspend fun keyup(ip: String, key: String) = call(ip, "POST", "/keyup/$key").ok

    /** Sends one typed character (spec 15.3 typing). */
    suspend fun literal(ip: String, ch: Char) =
        call(ip, "POST", "/keypress/Lit_" + URLEncoder.encode(ch.toString(), "UTF-8").replace("+", "%20")).ok

    /** POST /input?k=v to Lumen while it's open (spec 15.6). */
    suspend fun input(ip: String, params: Map<String, String>) = call(ip, "POST", "/input" + query(params)).ok
}
