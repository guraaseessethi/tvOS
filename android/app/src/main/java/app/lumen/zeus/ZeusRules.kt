package app.lumen.zeus

import app.lumen.ecp.RokuApp
import org.json.JSONObject

/** Spec 9.2 safety net for the brain's answers. Pure, so it unit-tests on the JVM. */
object ZeusRules {
    /**
     * The JSON object in a model reply, tolerating ```json fences or a sentence around it.
     * Returns null when there is no object at all (then the text itself is the reply).
     */
    fun extractJson(text: String): JSONObject? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
    }

    /** Turns any model reply into an Answer: JSON when it's there, plain words otherwise. */
    fun parse(text: String, apps: List<RokuApp>): Answer {
        extractJson(text)?.let { return clean(it, apps) }
        val plain = text.replace(Regex("```[a-z]*"), "").replace(Regex("\\s+"), " ").trim().take(240)
        return Answer(plain.ifEmpty { "Done." })
    }

    /** Keeps only well-formed actions with real IDs (spec 9.2: ignore anything else). */
    fun clean(o: JSONObject, apps: List<RokuApp>): Answer {
        val appIds = apps.filter { !it.isInput }.map { it.id }.toSet()
        val inputIds = apps.filter { it.isInput }.map { it.id }.toSet()
        val out = mutableListOf<Action>()
        val arr = o.optJSONArray("actions")
        for (i in 0 until (arr?.length() ?: 0)) {
            val a = arr!!.optJSONObject(i) ?: continue
            val minutes = a.optInt("minutes", -1)
            when (a.optString("type")) {
                "launch_app" -> a.optString("appId").takeIf { it in appIds }?.let { out += Action("launch_app", appId = it) }
                "switch_input" -> a.optString("inputId").takeIf { it in inputIds }?.let { out += Action("switch_input", inputId = it) }
                "keypress" -> a.optString("key").takeIf { it in Zeus.KEYS }?.let {
                    out += Action("keypress", key = it, repeat = a.optInt("repeat", 1).coerceIn(1, 10))
                }
                "power" -> a.optString("state").takeIf { it == "on" || it == "off" }?.let { out += Action("power", state = it) }
                "set_theme" -> a.optString("theme").takeIf { it in Zeus.THEMES }?.let { out += Action("set_theme", theme = it) }
                "clear_theme_override" -> out += Action("clear_theme_override")
                "set_sleep_timer" -> if (minutes in 5..240) out += Action("set_sleep_timer", minutes = minutes)
                "cancel_sleep_timer" -> out += Action("cancel_sleep_timer")
                "open_lumen" -> out += Action("open_lumen")
                "set_timer" -> a.optInt("seconds", -1).takeIf { it in 5..86_400 }?.let { out += Action("set_timer", seconds = it) }
                "cancel_timer" -> out += Action("cancel_timer")
                "set_alarm" -> a.optString("time").takeIf { Regex("^([01]?\\d|2[0-3]):[0-5]\\d$").matches(it) }
                    ?.let { out += Action("set_alarm", time = it.padStart(5, '0')) }
                "cancel_alarm" -> out += Action("cancel_alarm")
                "search" -> a.optString("query").trim().takeIf { it.isNotEmpty() }?.let { q ->
                    val app = a.optString("appId").takeIf { it in appIds }
                    out += Action("search", query = q.take(80), appId = app)
                }
                "start_game" -> a.optString("game").takeIf { it in Zeus.GAMES }?.let { out += Action("start_game", game = it) }
                "show_message" -> a.optString("text").takeIf { it.isNotBlank() }?.let { out += Action("show_message", text = it.take(80)) }
            }
        }
        val chipsArr = o.optJSONArray("chips")
        val chips = (0 until (chipsArr?.length() ?: 0)).mapNotNull { chipsArr!!.optString(it).takeIf { s -> s.isNotBlank() }?.take(28) }.take(4)
        val reply = o.optString("reply").replace(Regex("\\s+"), " ").trim().take(240)
        return Answer(reply.ifEmpty { "Done." }, out, chips)
    }
}
