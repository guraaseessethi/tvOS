package app.lumen.zeus

import android.content.Context
import android.util.Log
import app.lumen.auth.Auth
import app.lumen.ecp.RokuApp
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * Zeus's brain (spec 9): Gemini through Firebase AI Logic, so no API key ships in the APK.
 * Both the phone (voice, typing) and the TV (through the bridge's POST /zeus) ask here.
 *
 * Gemini 2.5 "thinks" before answering and that thinking counts against the output budget, so
 * the budget is generous. If a model isn't available on the project, the next one is tried.
 */
object ZeusBrain {
    private const val TAG = "Zeus"
    private val MODELS = listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-flash-latest", "gemini-2.0-flash")

    /** The last failure in plain words, shown in Setup > Check Zeus. */
    @Volatile var lastError: String = ""
        private set

    private val SYSTEM = """
        You are Zeus, the assistant built into Lumen, a calm Van Gogh-styled home screen on a Roku TV.
        Voice: warm, brief and specific. One or two sentences, under 30 words, no emoji, no exclamation marks,
        no lists. Replies may be read aloud. Use the person's name now and then if you know it.
        Be honest about limits: you cannot see inside apps or know what is playing, and you cannot change
        picture mode (say: press the star button while watching to change picture mode).
        You can act on the TV with these actions:
          launch_app {appId}, switch_input {inputId}, keypress {key, repeat}, power {state: on|off},
          set_theme {theme}, clear_theme_override, set_sleep_timer {minutes}, cancel_sleep_timer,
          open_lumen, show_message {text}.
        Rules:
        - Only use app and input IDs from the lists you are given. Never invent IDs.
        - keypress keys: VolumeUp, VolumeDown, VolumeMute, Play, Rev, Fwd, Home, Back, InstantReplay.
        - Use "power" with state "off" only when the person explicitly asks to turn the TV off.
        - Volume: "a bit" means repeat 3, "a lot" means repeat 8. Repeat is 1 to 10.
        - set_theme changes Lumen's painting: starry (Starry Night), sunflower (Sunflower Field),
          irises (Irises in the Rain), blossom (Almond Blossom), snow (Snowy Evening).
        - set_sleep_timer takes minutes from 5 to 240.
        - If you do something, say what you did in the reply. If nothing fits, say so instead of guessing.
        Answer with JSON only: {"reply": "...", "actions": [...], "chips": ["...", "..."]}
        with 2 to 4 short follow-up chips (under 5 words each) the person might tap next.
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

    private val models = HashMap<String, GenerativeModel>()
    @Volatile private var working: String? = null

    private fun model(name: String, structured: Boolean): GenerativeModel = models.getOrPut("$name/$structured") {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = name,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                if (structured) responseSchema = schema
                maxOutputTokens = 2048
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
            lastError = "Firebase isn't set up (google-services.json is missing)."
            return Answer("I need Firebase set up in the Lumen app before I can think. Apps, volume and timers still work.")
        }
        val appList = apps.filter { !it.isInput }
        val inputs = apps.filter { it.isInput }
        facts.put("apps", JSONArray(appList.map { JSONObject().put("id", it.id).put("name", it.name) }))
        facts.put("inputs", JSONArray(inputs.map { JSONObject().put("id", it.id).put("name", it.name) }))
        Auth.current(context)?.let { facts.put("person", it.name) }
        val prompt = "TV state: $facts\n\nThe person says: ${message.take(500)}"
        val past = history.takeLast(6).map { t ->
            content(role = if (t.fromZeus) "model" else "user") { text(t.text.take(500)) }
        }.dropWhile { it.role == "model" }

        // The model that worked last time first, then the rest; structured output first, then plain JSON.
        val order = (listOfNotNull(working) + MODELS).distinct()
        var failure: Exception? = null
        for (name in order) {
            for (structured in listOf(true, false)) {
                try {
                    val text = withTimeout(15_000) { model(name, structured).startChat(past).sendMessage(prompt).text }
                    if (text.isNullOrBlank()) { failure = IllegalStateException("$name returned an empty answer"); continue }
                    working = name
                    lastError = ""
                    return ZeusRules.parse(text, apps)
                } catch (e: TimeoutCancellationException) {
                    lastError = "Gemini took longer than 15 seconds."
                    return Answer("That took too long. Try again?", chips = listOf("Try again"))
                } catch (e: Exception) {
                    Log.w(TAG, "model $name structured=$structured failed", e)
                    failure = e
                    val msg = e.message.orEmpty()
                    // Project-level problems won't be fixed by another model.
                    if (offline(msg) || notEnabled(msg)) break
                }
            }
            val msg = failure?.message.orEmpty()
            if (offline(msg) || notEnabled(msg)) break
        }
        val msg = failure?.message.orEmpty()
        lastError = (failure?.javaClass?.simpleName ?: "Error") + ": " + msg.take(300)
        return when {
            offline(msg) -> Answer("I can't reach the internet right now, but I can still open apps, change volume and set timers.")
            notEnabled(msg) -> Answer("Turn on Firebase AI Logic with the Gemini Developer API in the Firebase console, then ask me again.")
            else -> Answer("I couldn't reach Gemini. Setup > Check Zeus shows why.", chips = listOf("Try again"))
        }
    }

    private fun offline(msg: String) =
        msg.contains("Unable to resolve host", true) || msg.contains("failed to connect", true) || msg.contains("network", true)

    private fun notEnabled(msg: String) =
        msg.contains("API has not been used", true) || msg.contains("is disabled", true) ||
            msg.contains("PERMISSION_DENIED", true) || msg.contains("API key not valid", true) ||
            msg.contains("firebasevertexai", true) || msg.contains("generativelanguage", true)

    /** Setup > Check Zeus: one tiny request, with the real reason when it fails. */
    suspend fun check(context: Context): String {
        val a = ask(context, "Say hello in five words.", emptyList(), emptyList(), JSONObject())
        return if (lastError.isEmpty()) "Zeus is working: \"${a.reply}\"" else "Zeus can't think yet. $lastError"
    }
}
