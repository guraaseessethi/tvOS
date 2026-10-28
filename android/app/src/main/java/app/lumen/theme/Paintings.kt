package app.lumen.theme

import androidx.compose.ui.graphics.Color
import app.lumen.R

/** The five paintings and their deep colours (spec 3.2). Keys match the TV. */
enum class Painting(
    val key: String,
    val title: String,
    val weather: String,
    val deep: Color,
    val sharp: Int,
    val blur: Int,
    val thumb: Int,
) {
    Starry("starry", "Starry Night", "clear", Color(0xFF14184A), R.drawable.wall_starry, R.drawable.wall_starry_blur, R.drawable.wall_starry_thumb),
    Sunflower("sunflower", "Sunflowers", "sunny", Color(0xFF703E00), R.drawable.wall_sunflower, R.drawable.wall_sunflower_blur, R.drawable.wall_sunflower_thumb),
    Irises("irises", "Irises", "rain", Color(0xFF2C2460), R.drawable.wall_irises, R.drawable.wall_irises_blur, R.drawable.wall_irises_thumb),
    Blossom("blossom", "Blossom", "cloud", Color(0xFF0A464E), R.drawable.wall_blossom, R.drawable.wall_blossom_blur, R.drawable.wall_blossom_thumb),
    Snow("snow", "Snowy Evening", "snow", Color(0xFF2E3068), R.drawable.wall_snow, R.drawable.wall_snow_blur, R.drawable.wall_snow_thumb);

    companion object {
        fun of(key: String?): Painting = entries.firstOrNull { it.key == key } ?: Starry

        /** Spec 8.2: WMO weather code to painting. */
        fun forWeather(code: Int, isDay: Boolean): Painting = of(ThemeRules.weatherKey(code, isDay))
    }
}

/** Tile pastels (spec 3.3), assigned by the same hash as the TV so an app keeps its colour. */
object Pastels {
    private val cards = listOf(
        R.drawable.card_rose, R.drawable.card_sky, R.drawable.card_mint, R.drawable.card_wheat,
        R.drawable.card_lilac, R.drawable.card_peach, R.drawable.card_aqua, R.drawable.card_cream,
    )

    fun index(appId: String): Int = ThemeRules.pastelIndex(appId)

    fun key(appId: String) = ThemeRules.PASTELS[index(appId)]
    fun card(appId: String) = cards[index(appId)]
}
