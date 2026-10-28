package app.lumen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.lumen.R

/** Brand constants (spec 3.1). Yellow is the only accent. */
object Ink {
    val yellow = Color(0xFFFFDE03)
    val ink = Color(0xFF1C1C1C)
    val white = Color.White
    val paper = Color(0xFFFBF7EC)
    val ink60 = Color(0x991C1C1C)
    val green = Color(0xFF7EE2A8)
    val okGreen = Color(0xFF1E9E5A)
    val amber = Color(0xFFE8A33A)
    val red = Color(0xFFD9534F)
}

/** Spec 4: Fraunces for display moments, Manrope for UI. */
object Fonts {
    val display = FontFamily(Font(R.font.fraunces_display))
    val title = FontFamily(Font(R.font.fraunces_title))
    val italic = FontFamily(Font(R.font.fraunces_italic, style = FontStyle.Italic))
    val manrope = FontFamily(
        Font(R.font.manrope_semibold, FontWeight.SemiBold),
        Font(R.font.manrope_bold, FontWeight.Bold),
        Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
    )
}

object T {
    fun ui(size: Int, weight: FontWeight = FontWeight.SemiBold, color: Color = Color.Unspecified, tracking: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontFamily = Fonts.manrope, fontWeight = weight, fontSize = size.sp, color = color, letterSpacing = tracking)

    fun eyebrow(size: Int = 12, color: Color = Color.Unspecified) = ui(size, FontWeight.ExtraBold, color, 0.16.em)
    fun title(size: Int, color: Color = Color.Unspecified) = TextStyle(fontFamily = Fonts.title, fontSize = size.sp, color = color, lineHeight = (size * 1.1).sp)
    fun italic(size: Int, color: Color = Color.Unspecified) = TextStyle(fontFamily = Fonts.italic, fontStyle = FontStyle.Italic, fontSize = size.sp, color = color)
}

@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ink.yellow, onPrimary = Ink.ink,
            surface = Ink.paper, onSurface = Ink.ink,
            background = Color(0xFF14184A), onBackground = Ink.white,
        ),
        content = content,
    )
}
