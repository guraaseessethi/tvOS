package app.lumen.ui.common

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lumen.R
import app.lumen.theme.Painting
import app.lumen.ui.theme.Ink
import app.lumen.ui.theme.T

/** Spec 15.5 shared frame: blurred painting, the sharp painting fading out over the top, deep gradient. */
@Composable
fun PaintingBackground(p: Painting, sharpHeight: Dp = 330.dp, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(p.deep)) {
        Image(
            painterResource(p.blur), null, Modifier.fillMaxSize().graphicsLayer { alpha = 0.55f },
            contentScale = ContentScale.Crop,
        )
        Image(
            painterResource(p.sharp), null,
            Modifier
                .fillMaxWidth()
                .height(sharpHeight)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(0.55f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                },
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to p.deep.copy(alpha = 0.35f), 0.45f to p.deep.copy(alpha = 0.7f), 1f to p.deep.copy(alpha = 0.92f)),
            ),
        )
    }
}

/** Frosted surface: deep at 55% with a white hairline (spec 3.2 + 15.5). */
fun Modifier.frosted(deep: Color, shape: Shape, alpha: Float = 0.55f) =
    this.clip(shape).background(deep.copy(alpha = alpha)).border(1.dp, Color.White.copy(alpha = 0.16f), shape)

fun haptic(view: View) {
    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
}

@Composable
fun ZeusMark(size: Dp, modifier: Modifier = Modifier, cream: Boolean = false) {
    Image(painterResource(if (cream) R.drawable.zeus_mark_cream_120 else R.drawable.zeus_mark_120), null, modifier.size(size))
}

@Composable
fun Eyebrow(text: String, color: Color = Color.White.copy(alpha = 0.85f), modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = T.eyebrow(12, color), modifier = modifier)
}

/** Ink button with yellow text (spec 13: primary buttons). */
@Composable
fun InkButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (enabled) Ink.ink else Ink.ink.copy(alpha = 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.ui(17, FontWeight.ExtraBold, Ink.yellow)) }
}

@Composable
fun YellowButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(if (enabled) Ink.yellow else Ink.yellow.copy(alpha = 0.5f))
            .border(2.dp, Ink.ink, RoundedCornerShape(28.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.ui(17, FontWeight.ExtraBold, Ink.ink)) }
}

@Composable
fun OutlineButton(text: String, modifier: Modifier = Modifier, color: Color = Ink.ink, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(BorderStroke(2.dp, color), RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.ui(15, FontWeight.Bold, color)) }
}

/** Ink-outlined chip (spec 15.5 "Try chips"). */
@Composable
fun Chip(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(2.dp, Ink.ink, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = T.ui(14, FontWeight.Bold, Ink.ink), maxLines = 1) }
}

/** A white text field on paper. */
@Composable
fun PaperField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    ime: ImeAction = ImeAction.Next,
) {
    TextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, style = T.ui(13, FontWeight.Bold)) },
        singleLine = true,
        textStyle = T.ui(16, FontWeight.SemiBold, Ink.ink),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard, imeAction = ime),
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedLabelColor = Ink.ink60,
            unfocusedLabelColor = Ink.ink60,
            cursorColor = Ink.ink,
            focusedTextColor = Ink.ink,
            unfocusedTextColor = Ink.ink,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Green check or amber ! row (spec 15.5 Setup checks). */
@Composable
fun CheckRow(ok: Boolean?, text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(
                when (ok) { true -> Ink.okGreen; false -> Ink.amber; null -> Color(0xFFBDB8AA) },
            ),
            contentAlignment = Alignment.Center,
        ) { Text(if (ok == true) "✓" else if (ok == false) "!" else "…", style = T.ui(13, FontWeight.ExtraBold, Color.White)) }
        Spacer(Modifier.width(14.dp))
        Text(text, style = T.ui(15, FontWeight.SemiBold, Ink.ink), modifier = Modifier.weight(1f))
    }
}

data class Tab(val label: String, val icon: ImageVector)

/** Spec 15.5 tab bar: 16dp from the edges, 68dp tall, radius 34. Active tab yellow. */
@Composable
fun TabBar(tabs: List<Tab>, selected: Int, deep: Color, onPaper: Boolean, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(34.dp)
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .fillMaxWidth()
            .height(68.dp)
            .let { if (onPaper) it.clip(shape).background(Color(0xFFEDE8DA)) else it.frosted(deep.copy(alpha = 1f), shape, 0.8f) },
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { i, t ->
            val on = i == selected
            val color = when {
                on && onPaper -> Ink.ink
                on -> Ink.yellow
                onPaper -> Ink.ink60
                else -> Color.White.copy(alpha = 0.75f)
            }
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable { onSelect(i) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.clip(RoundedCornerShape(14.dp)).background(if (on && onPaper) Ink.ink else Color.Transparent)
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                ) { Icon(t.icon, null, tint = if (on && onPaper) Ink.yellow else color, modifier = Modifier.size(24.dp)) }
                Spacer(Modifier.height(2.dp))
                Text(t.label, style = T.ui(12, FontWeight.ExtraBold, color), textAlign = TextAlign.Center)
            }
        }
    }
}
