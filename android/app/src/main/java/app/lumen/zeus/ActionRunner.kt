package app.lumen.zeus

import app.lumen.Lumen
import app.lumen.ecp.Screen
import app.lumen.theme.Painting
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/** Runs Zeus actions from the phone (spec 9.2, "Phone does" column). Returns receipt lines. */
object ActionRunner {

    suspend fun run(actions: List<Action>): List<String> {
        val ip = Lumen.ip() ?: return if (actions.isEmpty()) emptyList() else listOf("No TV set up yet")
        val ecp = Lumen.ecp
        val apps = Lumen.tv.value.apps
        val receipts = mutableListOf<String>()
        for (a in actions) {
            when (a.type) {
                "launch_app" -> {
                    val name = apps.firstOrNull { it.id == a.appId }?.name ?: "App"
                    receipts += if (ecp.launch(ip, a.appId!!)) "$name opened on TV" else "Couldn't open $name"
                }
                "switch_input" -> {
                    val app = apps.firstOrNull { it.id == a.inputId }
                    val name = Lumen.settings.value.inputName(a.inputId!!, app?.name ?: "Input")
                    receipts += if (ecp.launch(ip, a.inputId)) "Switched to $name" else "Couldn't switch to $name"
                }
                "keypress" -> {
                    repeat(a.repeat ?: 1) { ecp.keypress(ip, a.key!!); delay(120) }
                    receipts += when (a.key) {
                        "VolumeUp" -> "Volume up"
                        "VolumeDown" -> "Volume down"
                        "VolumeMute" -> "Mute toggled"
                        "Play" -> "Play/Pause"
                        else -> a.key!!
                    }
                }
                "power" -> {
                    val on = a.state == "on"
                    ecp.keypress(ip, if (on) "PowerOn" else "PowerOff")
                    receipts += if (on) "TV turning on" else "TV turned off"
                }
                "set_theme" -> {
                    val p = Painting.of(a.theme)
                    Lumen.repaint(p)
                    receipts += "Repainted: ${p.title}"
                }
                "clear_theme_override" -> {
                    Lumen.clearOverride()
                    receipts += "Following the weather"
                }
                "set_sleep_timer" -> {
                    val m = a.minutes ?: 30
                    Lumen.setSleep(m)
                    val at = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(System.currentTimeMillis() + m * 60_000L))
                    receipts += "Sleep timer $at"
                }
                "cancel_sleep_timer" -> {
                    Lumen.setSleep(0)
                    receipts += "Sleep timer off"
                }
                "open_lumen" -> receipts += if (ecp.launch(ip, "dev")) "Lumen opened" else "Couldn't open Lumen"
                "show_message" -> {
                    if (Lumen.tv.value.screen == Screen.Lumen) ecp.input(ip, mapOf("zeus" to JSONObject().put("reply", a.text).toString()))
                }
            }
        }
        return receipts
    }

    /**
     * Spec 15.6 + 7.1: every phone exchange also shows on the TV. Lumen opens its Zeus panel for 8 s.
     * If Lumen isn't on screen the TV ignores /input, which is fine.
     */
    suspend fun mirrorToTv(user: String, reply: String, receipts: List<String>) {
        val ip = Lumen.ip() ?: return
        val json = JSONObject().put("user", user.take(300)).put("reply", reply.take(500)).put("receipts", JSONArray(receipts.take(4)))
        Lumen.ecp.input(ip, mapOf("zeus" to json.toString()))
    }
}
