package app.lumen.zeus

/**
 * "Hey Zeus" in what speech recognition actually hears. Speech engines rarely spell Zeus right,
 * so a few sound-alikes count too.
 */
object WakePhrase {
    private val pattern = Regex(
        "\\b(hey|hay|hi|ok|okay|a)[ ,]+(zeus|zeuss|zues|zoos|zus|zoose|seuss|soos|suse|juice|deuce|choose)\\b[ ,.!?]*",
        RegexOption.IGNORE_CASE,
    )

    /** null when the wake phrase isn't there; otherwise what was said after it ("" if nothing). */
    fun after(heard: String): String? {
        val m = pattern.find(heard) ?: return null
        return heard.substring(m.range.last + 1).trim().trimEnd('.', '!', '?')
    }
}
