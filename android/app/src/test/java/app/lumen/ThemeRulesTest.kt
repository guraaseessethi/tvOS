package app.lumen

import app.lumen.theme.ThemeRules
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeRulesTest {
    /** Spec 8.2 mapping. */
    @Test fun weatherCodesPickTheRightPainting() {
        assertEquals("sunflower", ThemeRules.weatherKey(0, isDay = true))
        assertEquals("starry", ThemeRules.weatherKey(1, isDay = false))
        assertEquals("blossom", ThemeRules.weatherKey(3, isDay = true))
        assertEquals("blossom", ThemeRules.weatherKey(45, isDay = false))
        assertEquals("irises", ThemeRules.weatherKey(61, isDay = true))
        assertEquals("irises", ThemeRules.weatherKey(81, isDay = false))
        assertEquals("irises", ThemeRules.weatherKey(95, isDay = true))
        assertEquals("snow", ThemeRules.weatherKey(73, isDay = true))
        assertEquals("snow", ThemeRules.weatherKey(86, isDay = false))
        assertEquals("starry", ThemeRules.weatherKey(-1, isDay = false))
        assertEquals("sunflower", ThemeRules.weatherKey(-1, isDay = true))
    }

    /** Same values the Roku's PastelFor() gives, so an app has one colour on both screens. */
    @Test fun pastelHashMatchesTheTv() {
        val expected = mapOf("12" to "sky", "837" to "lilac", "13" to "mint", "22297" to "wheat", "dev" to "peach", "2213" to "cream")
        expected.forEach { (id, pastel) -> assertEquals(id, pastel, ThemeRules.PASTELS[ThemeRules.pastelIndex(id)]) }
    }
}
