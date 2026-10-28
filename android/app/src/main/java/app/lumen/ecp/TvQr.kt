package app.lumen.ecp

import java.net.URI
import java.net.URLDecoder

/** What the TV's "Connect your phone" QR code says: lumen://connect?ip=<tv ip>&name=<tv name>. */
object TvQr {
    data class Target(val ip: String, val name: String?)

    private val ipv4 = Regex("\\b((25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3})\\b")

    fun parse(raw: String?): Target? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (text.startsWith("lumen://", ignoreCase = true)) {
            val query = runCatching { URI(text).rawQuery }.getOrNull().orEmpty()
            val params = query.split("&").mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i) to URLDecoder.decode(it.substring(i + 1), "UTF-8")
            }.toMap()
            val ip = params["ip"]?.takeIf { ipv4.matches(it) } ?: return null
            return Target(ip, params["name"]?.takeIf { it.isNotBlank() })
        }
        // Anything else that carries a TV address, e.g. http://192.168.2.33:8060/
        if (text.contains(":8060") || text.matches(ipv4)) {
            return ipv4.find(text)?.let { Target(it.value, null) }
        }
        return null
    }
}
