package app.lumen.zeus

import android.content.Context
import app.lumen.Lumen
import app.lumen.ecp.Screen
import org.json.JSONObject
import java.time.OffsetDateTime

/** One exchange from the phone: local intents first, then the brain; runs the actions (spec 9.1, 9.3). */
object ZeusSession {
    data class Result(val user: String, val answer: Answer, val receipts: List<String>)

    suspend fun send(context: Context, text: String, history: List<Turn>): Result {
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
            ZeusBrain.ask(context, text, tv.apps, history, facts)
        }
        val receipts = ActionRunner.run(answer.actions)
        ActionRunner.mirrorToTv(text, answer.reply, receipts)
        return Result(text, answer, receipts)
    }
}
