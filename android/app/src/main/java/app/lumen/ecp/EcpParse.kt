package app.lumen.ecp

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.URLEncoder

/** ECP response parsing and URL building, free of Android types so it unit-tests on the JVM. */
object EcpParse {
    fun query(params: Map<String, String>): String =
        if (params.isEmpty()) "" else "?" + params.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8").replace("+", "%20")
        }

    fun screenOf(body: String): Screen? = when {
        body.contains("id=\"dev\"") -> Screen.Lumen
        Regex("<app>\\s*Roku\\s*</app>").containsMatchIn(body) || body.contains("type=\"home\"") -> Screen.Home
        body.contains("<app") -> Screen.Other
        else -> null
    }

    fun parseApps(xml: String): List<RokuApp> {
        val out = mutableListOf<RokuApp>()
        runCatching {
            val p = XmlPullParserFactory.newInstance().newPullParser()
            p.setInput(xml.reader())
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && p.name == "app") {
                    val id = p.getAttributeValue(null, "id") ?: ""
                    val type = p.getAttributeValue(null, "type") ?: ""
                    val name = p.nextText().trim()
                    if (id.isNotEmpty() && id != "dev") out += RokuApp(id, type, name)
                }
                ev = p.next()
            }
        }
        return out
    }

    /** <device-info><is-tv>true</is-tv>...</device-info> -> {"is-tv": "true", ...} */
    fun flat(xml: String): Map<String, String> {
        val out = mutableMapOf<String, String>()
        runCatching {
            val p = XmlPullParserFactory.newInstance().newPullParser()
            p.setInput(xml.reader())
            var depth = 0
            var cur: String? = null
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                when (ev) {
                    XmlPullParser.START_TAG -> { depth++; cur = if (depth == 2) p.name else null }
                    XmlPullParser.TEXT -> {
                        val name = cur
                        if (depth == 2 && name != null) out[name] = p.text.trim()
                    }
                    XmlPullParser.END_TAG -> { depth--; cur = null }
                }
                ev = p.next()
            }
        }
        return out
    }
}
