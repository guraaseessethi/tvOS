package app.lumen.zeus

import app.lumen.ecp.RokuApp
import org.json.JSONObject

/** Spec 9.2 safety net for the brain's answers. Pure, so it unit-tests on the JVM. */
object ZeusRules {
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
                "show_message" -> a.optString("text").takeIf { it.isNotBlank() }?.let { out += Action("show_message", text = it.take(80)) }
            }
        }
        val chipsArr = o.optJSONArray("chips")
        val chips = (0 until (chipsArr?.length() ?: 0)).mapNotNull { chipsArr!!.optString(it).takeIf { s -> s.isNotBlank() }?.take(28) }.take(4)
        val reply = o.optString("reply").replace(Regex("\\s+"), " ").trim().take(240)
        return Answer(reply.ifEmpty { "Done." }, out, chips)
    }
}
