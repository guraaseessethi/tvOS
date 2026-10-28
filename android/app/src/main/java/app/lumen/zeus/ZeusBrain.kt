package app.lumen.zeus

import android.content.Context
import app.lumen.auth.Auth
import app.lumen.ecp.RokuApp
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * Zeus's brain (spec 9): Gemini through Firebase AI Logic, so no API key ships in the APK.
 * Both the phone (voice, typing) and the TV (through the bridge's POST /zeus) ask here.
 */
object ZeusBrain {
    private const val MODEL = "gemini-2.5-flash"

    private val SYSTEM = """
        You are Zeus, the assistant built into Lumen, a calm Van Gogh-styled home screen on a Roku TV.
        Voice: warm, brief and specific. One or two sentences, under 30 words, no emoji, no exclamation marks,
        no lists. Replies may be read aloud. Use the person's name now and then if you know it.
        Be honest about limits: you cannot see inside apps or know what is playing, and you cannot change
        picture mode (say: press the star button while watching to change picture mode).
        You can act on the TV with the actions in the schema. Rules:
        - Only use app and input IDs from the lists you are given. Never invent IDs.
        - Use "power" with state "off" only when the person explicitly asks to turn the TV off. Use "on" only when asked to turn it on.
        - Volume: "a bit" means repeat 3, "a lot" means repeat 8. Repeat is 1 to 10.
        - set_theme changes Lumen's painting: starry (Starry Night), sunflower (Sunflower Field),
          irises (Irises in the Rain), blossom (Almond Blossom), snow (Snowy Evening).
        - set_sleep_timer takes minutes from 5 to 240.
        - open_lumen opens the Lumen home screen on the TV.
        - If you do something, say what you did in the reply. If nothing fits, say so instead of guessing.
        Always return 2 to 4 short follow-up chips (under 5 words each) the person might tap next.
    """.trimIndent()

    private val schema = Schema.obj(
        mapOf(
            "reply" to Schema.string(),
            "actions" to Schema.array(
                Schema.obj(
                    mapOf(
                        "type" to Schema.enumeration(Zeus.ACTION_TYPES),
                        "appId" to Schema.string(),
                        "inputId" to Schema.string(),
                        "key" to Schema.enumeration(Zeus.KEYS),
                        "repeat" to Schema.integer(),
                        "state" to Schema.enumeration(listOf("on", "off")),
                        "theme" to Schema.enumeration(Zeus.THEMES),
                        "minutes" to Schema.integer(),
                        "text" to Schema.string(),
                    ),
                    listOf("appId", "inputId", "key", "repeat", "state", "theme", "minutes", "text"),
                ),
            ),
            "chips" to Schema.array(Schema.string()),
        ),
    )

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
                maxOutputTokens = 400
                temperature = 0.6f
            },
            systemInstruction = content { text(SYSTEM) },
        )
    }

    /** facts: painting, weather, time, sleep timer… anything that helps. */
    suspend fun ask(
        context: Context,
        message: String,
        apps: List<RokuApp>,
        history: List<Turn>,
        facts: JSONObject,
    ): Answer {
        if (!Auth.ready(context)) {
            return Answer("I need Firebase set up in the Lumen app before I can think. Apps, volume and timers still work.")
        }
        val appList = apps.filter { !it.isInput }
        val inputs = apps.filter { it.isInput }
        facts.put("apps", appList.map { JSONObject().put("id", it.id).put("name", it.name) }.let { org.json.JSONArray(it) })
        facts.put("inputs", inputs.map { JSONObject().put("id", it.id).put("name", it.name) }.let { org.json.JSONArray(it) })
        Auth.current(context)?.let { facts.put("person", it.name) }

        val past = history.takeLast(6).map { t ->
            content(role = if (t.fromZeus) "model" else "user") { text(t.text.take(500)) }
        }.dropWhile { it.role == "model" }

        return try {
            val text = withTimeout(12_000) {
                model.startChat(past).sendMessage("TV state: $facts\n\n${message.take(500)}").text
            } ?: return Answer("I lost my train of thought. Ask me again?", chips = listOf("Try again"))
            ZeusRules.clean(JSONObject(text), apps)
        } catch (e: TimeoutCancellationException) {
            Answer("That took too long. Try again?", chips = listOf("Try again"))
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            when {
                msg.contains("API has not been used", true) || msg.contains("PERMISSION_DENIED", true) ->
                    Answer("Turn on Firebase AI Logic in the Firebase console, then ask me again.")
                msg.contains("Unable to resolve host", true) || msg.contains("timeout", true) ->
                    Answer("I can't reach the internet right now, but I can still open apps, change volume and set timers.")
                else -> Answer("That didn't go through. Try again?", chips = listOf("Try again"))
            }
        }
    }
}
