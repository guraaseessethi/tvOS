package app.lumen.zeus

import android.content.Context
import app.lumen.Lumen
import app.lumen.ecp.Screen
import org.json.JSONObject
import java.time.OffsetDateTime

/** One exchange from the phone: local intents first, then the brain; runs the actions (spec 9.1, 9.3). */
object ZeusSession {
    /** spokeOnTv: the TV is saying the reply, so the phone stays quiet. */
    data class Result(val user: String, val answer: Answer, val receipts: List<String>, val spokeOnTv: Boolean = false)

    suspend fun send(context: Context, text: String): Result {
        if (Lumen.tv.value.apps.isEmpty()) Lumen.refreshTv()
        val tv = Lumen.tv.value
        val s = Lumen.settings.value
        val answer = LocalIntents.match(text, tv.apps, s.inputNames) ?: run {
            val facts = JSONObject()
                .put("source", "phone_voice")
                .put("localTime", OffsetDateTime.now().toString())
                .put("painting", Lumen.painting.value.key)
                .put("tv", JSONObject().put("power", tv.info?.powerMode ?: "unknown").put("lumenOpen", tv.screen == Screen.Lumen))
                .put("sleepTimerMinutesLeft", Lumen.sleepMinutesLeft())
                .put("inputNames", JSONObject(s.inputNames as Map<*, *>))
            Lumen.weather.value?.let { facts.put("weather", it.line(s.fahrenheit)) }
            ZeusBrain.ask(context, text, tv.apps, ZeusLog.history(), facts)
        }
        val receipts = ActionRunner.run(answer.actions)
        ZeusLog.add(ZeusLog.Exchange(text, answer.reply, receipts, fromTv = false))
        val quiet = answer.actions.any { it.type == "start_game" }
        // Owner choice: Zeus answers on the TV (screen + speakers) whenever Lumen is showing.
        // The phone only speaks when Lumen isn't on screen (e.g. you're inside Netflix).
        val ip = Lumen.ip()
        val onTv = s.replyOnTv && ip != null && Lumen.ecp.activeScreen(ip) == Screen.Lumen
        var voice: String? = null
        if (onTv && s.spokenReplies && !quiet) {
            val clip = VoiceStudio.render(answer.reply, s.voiceName)
            val base = Lumen.bridgeUrl(ip!!)
            if (clip != null && base != null) voice = "$base/voice/$clip"
        }
        ActionRunner.mirrorToTv(text, answer.reply, receipts, quiet = quiet, voice = voice)
        return Result(text, answer, receipts, spokeOnTv = onTv && voice != null)
    }
}
