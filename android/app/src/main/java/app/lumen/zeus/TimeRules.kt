package app.lumen.zeus

import java.time.ZonedDateTime

/** Pure time maths for timers and alarms (unit-tested on the JVM). */
object TimeRules {
    private val words = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "fifteen" to 15, "twenty" to 20,
        "thirty" to 30, "forty" to 40, "forty five" to 45, "fifty" to 50, "sixty" to 60, "ninety" to 90,
    )
    private val unitRe = Regex("\\b(\\d{1,3}|forty five|an|a|one|two|three|four|five|six|seven|eight|nine|ten|fifteen|twenty|thirty|forty|fifty|sixty|ninety) ?(hours?|hrs?|minutes?|mins?|seconds?|secs?)\\b")

    /** "10 minutes", "1 hour 30 minutes", "half an hour", "90 seconds" → seconds; null if none. */
    fun durationSeconds(text: String): Int? {
        var total = 0
        if (Regex("\\b(an|one) hour and a half\\b").containsMatchIn(text)) return 5400
        val half = Regex("\\bhalf an hour\\b")
        if (half.containsMatchIn(text)) total += 1800
        // Take "half an hour" out first so its "an hour" isn't counted again.
        val t = half.replace(text, " ")
        for (m in unitRe.findAll(t)) {
            val n = m.groupValues[1].toIntOrNull() ?: words[m.groupValues[1]] ?: continue
            total += when (m.groupValues[2].first()) { 'h' -> n * 3600; 'm' -> n * 60; else -> n }
        }
        return total.takeIf { it > 0 }
    }

    /**
     * The next time the clock reads [hour]:[minute] (am/pm optional). Without am/pm, an hour of 1–12
     * means whichever of am/pm comes first ("7" at 9 pm is 7 am tomorrow; at 6 am it's 7 am today).
     */
    fun nextAlarm(hour: Int, minute: Int, ampm: String?, now: ZonedDateTime): ZonedDateTime? {
        if (hour !in 0..23 || minute !in 0..59) return null
        val candidates = when {
            ampm == "am" && hour in 1..12 -> listOf(hour % 12)
            ampm == "pm" && hour in 1..12 -> listOf(hour % 12 + 12)
            ampm != null -> return null
            hour in 1..12 -> listOf(hour % 12, hour % 12 + 12)
            else -> listOf(hour)
        }
        return candidates.flatMap { h ->
            val today = now.withHour(h).withMinute(minute).withSecond(0).withNano(0)
            listOf(today, today.plusDays(1))
        }.filter { it.isAfter(now) }.minOrNull()
    }

    /** "7 am", "6 30 pm", "6:30", "19:45", "at 7 o clock" in normalised text → hour, minute, am/pm. */
    fun clockIn(t: String): Triple<Int, Int, String?>? {
        val m = Regex("\\b(\\d{1,2})(?:[: ](\\d{2}))? ?(a ?m|p ?m)?\\b(?! ?(hours?|hrs?|minutes?|mins?|seconds?|secs?))").find(t) ?: return null
        val ampm = m.groupValues[3].replace(" ", "").ifEmpty { null }
        return Triple(m.groupValues[1].toInt(), m.groupValues[2].toIntOrNull() ?: 0, ampm)
    }

    /** "10 minutes", "1 hour 5 minutes", "45 seconds" for receipts and speech. */
    fun describe(seconds: Int): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        val parts = buildList {
            if (h > 0) add(if (h == 1) "1 hour" else "$h hours")
            if (m > 0) add(if (m == 1) "1 minute" else "$m minutes")
            if (s > 0 && h == 0) add(if (s == 1) "1 second" else "$s seconds")
        }
        return parts.joinToString(" ").ifEmpty { "0 seconds" }
    }
}
