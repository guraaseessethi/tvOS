package app.lumen.theme

/** Pure theme rules shared with the TV, kept free of Android types so they unit-test on the JVM. */
object ThemeRules {
    val PASTELS = listOf("rose", "sky", "mint", "wheat", "lilac", "peach", "aqua", "cream")

    /** Spec 8.2: WMO weather code to painting key. */
    fun weatherKey(code: Int, isDay: Boolean): String = when (code) {
        0, 1 -> if (isDay) "sunflower" else "starry"
        2, 3, 45, 48 -> "blossom"
        in 51..67, in 80..82, in 95..99 -> "irises"
        in 71..77, 85, 86 -> "snow"
        else -> if (isDay) "sunflower" else "starry"
    }

    /** PLAN 2: h = (h * 31 + ord(c)) mod 1000003, pastel = h mod 8. Must match the Roku exactly. */
    fun pastelIndex(appId: String): Int {
        var h = 0L
        for (c in appId) h = (h * 31 + c.code) % 1000003
        return (h % 8).toInt()
    }
}
