package app.lumen.zeus

import org.json.JSONArray
import org.json.JSONObject

/** One allowed action (spec 9.2). Unused fields stay null. */
data class Action(
    val type: String,
    val appId: String? = null,
    val inputId: String? = null,
    val key: String? = null,
    val repeat: Int? = null,
    val state: String? = null,
    val theme: String? = null,
    val minutes: Int? = null,
    val text: String? = null,
    val game: String? = null,
    /** set_timer: length in seconds. */
    val seconds: Int? = null,
    /** set_alarm: "HH:MM", 24-hour. */
    val time: String? = null,
    /** search: what to look for (a show, movie, actor…). appId optionally narrows it to one app. */
    val query: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("type", type)
        appId?.let { put("appId", it) }
        inputId?.let { put("inputId", it) }
        key?.let { put("key", it) }
        repeat?.let { put("repeat", it) }
        state?.let { put("state", it) }
        theme?.let { put("theme", it) }
        minutes?.let { put("minutes", it) }
        text?.let { put("text", it) }
        game?.let { put("game", it) }
        seconds?.let { put("seconds", it) }
        time?.let { put("time", it) }
        query?.let { put("query", it) }
    }
}

data class Answer(val reply: String, val actions: List<Action> = emptyList(), val chips: List<String> = emptyList()) {
    fun toJson(): JSONObject = JSONObject()
        .put("reply", reply)
        .put("actions", JSONArray().apply { actions.forEach { put(it.toJson()) } })
        .put("chips", JSONArray(chips))
}

data class Turn(val fromZeus: Boolean, val text: String)

object Zeus {
    val THEMES = listOf("starry", "sunflower", "irises", "blossom", "snow")
    val KEYS = listOf("VolumeUp", "VolumeDown", "VolumeMute", "Play", "Rev", "Fwd", "Home", "Back", "InstantReplay")
    val ACTION_TYPES = listOf(
        "launch_app", "switch_input", "keypress", "power", "set_theme", "clear_theme_override",
        "set_sleep_timer", "cancel_sleep_timer", "open_lumen", "show_message", "start_game",
        "set_timer", "cancel_timer", "set_alarm", "cancel_alarm", "search",
    )
    /** The TV's games (GameKit ids). */
    val GAMES = listOf("bird", "dog", "catch")

    /** Answer to "what can you do?" (owner request). */
    val ABILITIES = "Quite a bit, actually. I can open your apps or switch inputs, turn the volume up or down, " +
        "find a show or movie in your apps, pause things, set timers and alarms, set a sleep timer, repaint the screen, turn the TV off, or start a game like Flutter. Just ask."
    val DEFAULT_CHIPS = listOf("What can you do?", "Paint me something sunny", "Sleep in 30 min")
}
