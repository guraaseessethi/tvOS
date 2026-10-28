package app.lumen.zeus

import app.lumen.ecp.RokuApp

/**
 * Spec 9.3: obvious commands run instantly without the network.
 * Returns null when the message needs the real Zeus.
 */
object LocalIntents {
    private val numbers = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "ten" to 10,
        "fifteen" to 15, "twenty" to 20, "thirty" to 30, "forty" to 40, "forty five" to 45, "sixty" to 60, "ninety" to 90,
    )

    fun match(raw: String, apps: List<RokuApp>, inputNames: Map<String, String>): Answer? {
        val t = raw.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return null
        if (Regex("^(what (can|do) you do|what are you able to do|what can i (ask|say)|help|what can zeus do|how do you work)( for me)?$").matches(t))
            return Answer(Zeus.ABILITIES, emptyList(), listOf("Open YouTube", "Play Flutter", "Sleep in 30 min", "Paint me something sunny"))
        gameIn(t)?.let { (id, name) ->
            return Answer("Starting $name on the TV. Good luck.", listOf(Action("start_game", game = id)), listOf("Play Pup Run", "Play Star Catcher", "Go home"))
        }
        val amount = when {
            t.contains("a lot") || t.contains("way") -> 8
            t.contains("a bit") || t.contains("little") -> 3
            else -> Regex("\\b(\\d{1,2})\\b").find(t)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 10) ?: 2
        }

        if (Regex("^(turn |switch )?(the )?volume up|^(turn it |make it )?louder|^(turn )?it up$").containsMatchIn(t))
            return Answer("Turning it up.", listOf(Action("keypress", key = "VolumeUp", repeat = amount)), listOf("Louder", "Quieter", "Mute"))
        if (Regex("^(turn |switch )?(the )?volume down|^(turn it |make it )?(quieter|softer)|^(turn )?it down$").containsMatchIn(t))
            return Answer("Turning it down.", listOf(Action("keypress", key = "VolumeDown", repeat = amount)), listOf("Louder", "Quieter", "Mute"))
        if (Regex("^(un)?mute( it| the tv)?$").matches(t))
            return Answer("Done.", listOf(Action("keypress", key = "VolumeMute")), listOf("Louder", "Quieter"))
        if (Regex("^(pause|play|resume|stop)( it| the (show|movie|video))?$").matches(t))
            return Answer("Done.", listOf(Action("keypress", key = "Play")), listOf("Rewind", "Go home"))
        if (Regex("^(rewind|go back a bit)$").matches(t))
            return Answer("Rewinding.", listOf(Action("keypress", key = "InstantReplay")), listOf("Pause"))
        if (Regex("^(go )?(back )?home$|^go to home$|^open lumen$|^(show|open) (the )?home( screen)?$").matches(t))
            return Answer("Back to Lumen.", listOf(Action("open_lumen")), Zeus.DEFAULT_CHIPS)
        // Before power: "turn off the timer" must not turn the TV off.
        if (Regex("(cancel|stop|turn off|clear) (the )?(sleep )?timer").containsMatchIn(t))
            return Answer("Sleep timer cancelled.", listOf(Action("cancel_sleep_timer")), listOf("Sleep in 30 min"))
        if (Regex("^(turn|switch|shut) (off|it off|the tv off|off the tv)|^(power off|tv off)$").containsMatchIn(t))
            return Answer("Turning the TV off. Good night.", listOf(Action("power", state = "off")), listOf("Turn the TV on"))
        if (Regex("^(turn|switch) (on|it on|the tv on|on the tv)|^(power on|tv on)$").containsMatchIn(t))
            return Answer("Turning the TV on.", listOf(Action("power", state = "on")), Zeus.DEFAULT_CHIPS)

        Regex("sleep (timer )?(in |for )?(.+?) ?(minutes?|mins?|hours?|hrs?)\\b").find(t)?.let { m ->
            val n = m.groupValues[3].trim().let { it.toIntOrNull() ?: numbers[it] ?: (if (it.contains("half")) -1 else null) }
            val hours = m.groupValues[4].startsWith("h")
            val minutes = when {
                n == -1 -> 30
                n == null -> null
                hours -> n * 60
                else -> n
            }
            if (minutes != null && minutes in 5..240)
                return Answer("Sleep timer set for $minutes minutes.", listOf(Action("set_sleep_timer", minutes = minutes)), listOf("Cancel the timer"))
        }
        if (Regex("sleep (in )?half an hour").containsMatchIn(t))
            return Answer("Sleep timer set for 30 minutes.", listOf(Action("set_sleep_timer", minutes = 30)), listOf("Cancel the timer"))

        Regex("^(open|launch|start|play|put on|go to) (the )?(.+?)( app)?( on the tv)?$").find(t)?.let { m ->
            val want = m.groupValues[3].trim()
            apps.filter { !it.isInput }.firstOrNull { norm(it.name) == want || norm(it.name).startsWith(want) }
                ?.let { return Answer("Opening ${it.name}.", listOf(Action("launch_app", appId = it.id)), listOf("Go home", "Pause")) }
        }
        Regex("^(switch|change|go) to (the )?(.+)$").find(t)?.let { m ->
            val want = m.groupValues[3].trim()
            apps.filter { it.isInput }.firstOrNull {
                val name = norm(inputNames[it.id] ?: it.name)
                name == want || name.startsWith(want) || norm(it.name) == want ||
                    want.replace(" ", "") == it.id.substringAfter("tvinput.")
            }?.let { return Answer("Switching to ${inputNames[it.id] ?: it.name}.", listOf(Action("switch_input", inputId = it.id)), listOf("Go home")) }
        }
        return null
    }

    /** "play flutter", "start pup run", "let's play star catcher", "open the bird game"… */
    private fun gameIn(t: String): Pair<String, String>? {
        if (!Regex("^(let s |lets )?(play|start|open|launch|put on)\\b").containsMatchIn(t)) return null
        return when {
            Regex("\\b(flutter|flappy|bird game|the bird)\\b").containsMatchIn(t) -> "bird" to "Flutter"
            Regex("\\b(pup run|pup|dog game|the dog|dino|runner)\\b").containsMatchIn(t) -> "dog" to "Pup Run"
            Regex("\\b(star catcher|stars?|catch(ing)? game)\\b").containsMatchIn(t) -> "catch" to "Star Catcher"
            Regex("\\b(a )?game\\b").containsMatchIn(t) -> "bird" to "Flutter"
            else -> null
        }
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
}
