package app.lumen.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import app.lumen.ui.theme.Ink

/** The Zeus mark drawn live (spec 5.1): disc, 8 dashed brush strokes, centre dot. Colours swap for the inverted form. */
@Composable
fun ZeusStar(size: Dp, modifier: Modifier = Modifier, disc: Color = Ink.yellow, line: Color = Ink.ink) {
    Canvas(modifier.size(size)) {
        val u = this.size.minDimension / 64f
        drawCircle(disc, radius = 30f * u)
        rotate(-12f) {
            drawCircle(
                line, radius = 17f * u,
                style = Stroke(width = 4f * u, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * u, 7.35f * u))),
            )
        }
        drawCircle(line, radius = 7f * u)
    }
}
