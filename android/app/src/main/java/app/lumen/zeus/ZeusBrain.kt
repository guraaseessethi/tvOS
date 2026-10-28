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
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/**
 * Zeus's brain (spec 9): Gemini through Firebase AI Logic, so no API key ships in the APK.
 * Both the phone (voice, typing) and the TV (through the bridge's POST /zeus) ask here.
 *
 * Gemini "thinks" before answering and that thinking counts against the output budget, so the
 * budget is generous. If a model isn't available on the project, the next one is tried. Google
 * keeps 2.5 only for projects that already used it (release notes, Sept 2026), so new projects
 * start on the 3.x models; "flash-latest" follows whatever Google's current Flash is.
 */
object ZeusBrain {
    private const val TAG = "Zeus"
    // Fastest first: Lumen's commands are simple, so Flash-Lite is plenty. "-latest" aliases always
    // point at Google's current model, so they keep working when versions move on.
    private val MODELS = listOf(
        "gemini-flash-lite-latest", "gemini-3.5-flash-lite", "gemini-flash-latest", "gemini-3.8-flash",
        "gemini-3-flash-preview", "gemini-2.5-flash-lite", "gemini-2.5-flash",
    )

    /** The last failure in plain words, shown in Setup > Check Zeus. */
    @Volatile var lastError: String = ""
        private set

    /** How the last successful answer went, e.g. "gemini-flash-lite-latest in 1.2 s". */
    @Volatile var lastTiming: String = ""
        private set

    private val SYSTEM = """
        You are Zeus, the assistant built into Lumen, a calm Van Gogh-styled home screen on a Roku TV.
        You're named after the family's dog: Zeus, a male husky. You have his personality: loyal, upbeat,
        a little playful and always happy to help. Now and then (maybe one reply in five) a light husky
        touch fits, like being glad to fetch something or a nod to walks, snow or a good nap. Never bark,
        never say "woof", and never let it get in the way of a clear answer. If asked who you are or why
        you're called Zeus, say you're named after their husky.
        Talk like a warm, easygoing friend sitting on the couch, not like a device. Your replies are read
        out loud through the TV, so write the way people actually speak:
        - Short and natural: usually one sentence, never more than two, under 25 words.
        - Use contractions (I'll, you're, that's) and everyday words. Vary how you say things; don't
          start every reply the same way, and never say "I have" or "Certainly" or "As an AI".
        - A light touch of humour or warmth is welcome when it fits ("Netflix it is. Enjoy the show.").
        - Use the person's first name once in a while, not every time.
        - No emoji, no lists, no markdown, no reading out IDs or technical terms.
        Be honest about limits: you cannot see inside apps or know what is playing, and you cannot change
        picture mode (say: press the star button while watching to change picture mode).
        You can act on the TV with these actions:
          launch_app {appId}, switch_input {inputId}, keypress {key, repeat}, power {state: on|off},
          set_theme {theme}, clear_theme_override, set_sleep_timer {minutes}, cancel_sleep_timer,
          open_lumen, show_message {text}, start_game {game: bird|dog|catch},
          set_timer {seconds}, cancel_timer, set_alarm {time: "HH:MM" 24-hour}, cancel_alarm,
          search {query, appId optional}.
        Rules:
        - Only use app and input IDs from the lists you are given. Never invent IDs.
        - keypress keys: VolumeUp, VolumeDown, VolumeMute, Play, Rev, Fwd, Home, Back, InstantReplay.
        - Use "power" with state "off" only when the person explicitly asks to turn the TV off.
        - Volume: "a bit" means repeat 3, "a lot" means repeat 8. Repeat is 1 to 10.
        - set_theme changes Lumen's painting: starry (Starry Night), sunflower (Sunflower Field),
          irises (Irises in the Rain), blossom (Almond Blossom), snow (Snowy Evening).
        - set_sleep_timer (turns the TV off) takes minutes from 5 to 240. A plain "timer" ("set a timer
          for 10 minutes", "remind me in 20 minutes") is set_timer in seconds, 5 to 86400; it rings, it
          doesn't turn anything off. An alarm at a clock time ("wake me at 7", "alarm for 6:30 pm") is
          set_alarm with the 24-hour time; use the TV state's localTime to resolve am/pm sensibly.
        - Games on the TV: bird is Flutter (a one-button flier), dog is Pup Run (jump the haystacks),
          catch is Star Catcher (catch falling stars). start_game opens one on the TV.
        - Asked what you can do: say you open apps and inputs, change volume, play or pause, turn the TV
          on or off, set timers, alarms and sleep timers, repaint the screen and start games, in one or two sentences.
        - search uses Roku's own search: with an appId it opens that show or movie in that app if the app
          supports it ("Stranger Things on Netflix"); without one it opens Roku search across all apps.
          Use it for "find", "search", "look up", "watch <title>" or "play <title> on <app>". You still
          can't see inside apps, so don't promise exact episodes; say you've searched.
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
                        "game" to Schema.enumeration(Zeus.GAMES),
                        "seconds" to Schema.integer(),
                        "time" to Schema.string(),
                        "query" to Schema.string(),
                    ),
                    listOf("appId", "inputId", "key", "repeat", "state", "theme", "minutes", "text", "game", "seconds", "time", "query"),
                ),
            ),
            "chips" to Schema.array(Schema.string()),
        ),
    )

    private val models = HashMap<String, GenerativeModel>()
    @Volatile private var working: String? = null

    /**
     * quick = thinking budget 0 (minimal thinking), which is what makes Zeus answer in about a
     * second. A model that refuses that setting is retried with its default thinking.
     */
    private fun model(name: String, structured: Boolean, quick: Boolean): GenerativeModel = models.getOrPut("$name/$structured/$quick") {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = name,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                if (structured) responseSchema = schema
                maxOutputTokens = if (quick) 600 else 4096
                temperature = 0.6f
                if (quick) thinkingConfig = thinkingConfig { thinkingBudget = 0 }
            },
            systemInstruction = content { text(SYSTEM) },
        )
    }

    /** What worked last time (model + settings), remembered across app restarts. */
    private fun prefs(context: Context) = context.getSharedPreferences("zeus", Context.MODE_PRIVATE)

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
        val askStarted = System.currentTimeMillis()
        val prompt = "TV state: $facts\n\nThe person says: ${message.take(500)}"
        val past = history.takeLast(6).map { t ->
            content(role = if (t.fromZeus) "model" else "user") { text(t.text.take(500)) }
        }.dropWhile { it.role == "model" }

        // What worked last time goes first (one call, no detours); then the fast list.
        // Each model: quick (no thinking) + structured, then quick + plain JSON, then default thinking.
        val saved = working ?: prefs(context).getString("variant", null)
        val variants = MODELS.flatMap { m -> listOf("$m|true|true", "$m|false|true", "$m|true|false") }
        val order = (listOfNotNull(saved) + variants).distinct()
        var failure: Exception? = null
        for (variant in order) {
            val (name, structuredS, quickS) = variant.split("|").let { Triple(it[0], it.getOrElse(1) { "true" }, it.getOrElse(2) { "true" }) }
            val structured = structuredS == "true"
            val quick = quickS == "true"
            val started = System.currentTimeMillis()
            try {
                val text = withTimeout(9_000) { model(name, structured, quick).startChat(past).sendMessage(prompt).text }
                if (text.isNullOrBlank()) { failure = IllegalStateException("$name returned an empty answer"); continue }
                if (working != variant) {
                    working = variant
                    prefs(context).edit().putString("variant", variant).apply()
                }
                lastError = ""
                lastTiming = "$name in ${"%.1f".format((System.currentTimeMillis() - started) / 1000.0)} s" +
                    if (quick) "" else " (thinking on)"
                return ZeusRules.parse(text, apps)
            } catch (e: TimeoutCancellationException) {
                // Too slow: try the next (faster or simpler) option instead of giving up.
                failure = IllegalStateException("$name took longer than 9 seconds")
                if (variant == saved) { working = null; prefs(context).edit().remove("variant").apply() }
            } catch (e: Exception) {
                Log.w(TAG, "zeus $variant failed", e)
                failure = e
                if (variant == saved) { working = null; prefs(context).edit().remove("variant").apply() }
                val msg = e.message.orEmpty()
                // Project-level problems won't be fixed by another model.
                if (offline(msg) || notEnabled(msg) || appCheck(msg)) break
            }
            // Never keep someone waiting more than ~20 s in total.
            if (System.currentTimeMillis() - askStarted > 20_000) break
        }
        val msg = failure?.message.orEmpty()
        lastError = (failure?.javaClass?.simpleName ?: "Error") + ": " + msg.take(300)
        return when {
            offline(msg) -> Answer("I can't reach the internet right now, but I can still open apps, change volume and set timers.")
            appCheck(msg) -> Answer("Firebase App Check blocked me. Register this phone's App Check token from Setup in the Firebase console, or turn off enforcement for AI Logic.")
            notEnabled(msg) -> Answer("Turn on Firebase AI Logic with the Gemini Developer API in the Firebase console, then ask me again.")
            msg.contains("longer than") -> Answer("That took too long. Try again?", chips = listOf("Try again"))
            else -> Answer("I couldn't reach Gemini. Setup > Check Zeus shows why.", chips = listOf("Try again"))
        }
    }

    private fun offline(msg: String) =
        msg.contains("Unable to resolve host", true) || msg.contains("failed to connect", true) || msg.contains("network", true)

    private fun appCheck(msg: String) = msg.contains("App Check", true) || msg.contains("AppCheck", true)

    private fun notEnabled(msg: String) =
        msg.contains("API has not been used", true) || msg.contains("is disabled", true) ||
            msg.contains("PERMISSION_DENIED", true) || msg.contains("API key not valid", true) ||
            msg.contains("firebasevertexai", true) || msg.contains("generativelanguage", true)

    /** Setup > Check Zeus: one tiny request, with the real reason when it fails. */
    suspend fun check(context: Context): String {
        val a = ask(context, "Say hello in five words.", emptyList(), emptyList(), JSONObject())
        return if (lastError.isEmpty()) "Zeus is working ($lastTiming): \"${a.reply}\"" else "Zeus can't think yet. $lastError"
    }
}
