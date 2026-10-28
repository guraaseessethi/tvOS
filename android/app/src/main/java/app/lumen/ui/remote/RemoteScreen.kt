package app.lumen.ui.remote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lumen.Lumen
import app.lumen.theme.Painting
import app.lumen.ui.common.ZeusStar
import app.lumen.ui.common.frosted
import app.lumen.ui.common.haptic
import app.lumen.ui.theme.Ink
import app.lumen.ui.theme.T
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Spec 15.5 Remote tab. */
@Composable
fun RemoteScreen(painting: Painting, onZeus: () -> Unit) {
    val tv by Lumen.tv.collectAsState()
    val settings by Lumen.settings.collectAsState()
    val weather by Lumen.weather.collectAsState()
    val view = LocalView.current
    var confirmOff by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }
    val press: (String) -> Unit = { k -> haptic(view); Lumen.key(k) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(if (tv.info?.isTv == false) "ROKU" else "ROKU TV", style = T.eyebrow(12, Color.White.copy(alpha = 0.85f)))
                Text(settings.tvName.ifEmpty { "Your TV" }, style = T.title(28, Color.White), maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (tv.isOn) Ink.green else Color.White.copy(alpha = 0.4f)))
                    Spacer(Modifier.width(6.dp))
                    Text(tv.status, style = T.ui(13, FontWeight.Bold, Color.White.copy(alpha = 0.9f)))
                }
            }
            Box(
                Modifier.size(52.dp).frosted(painting.deep, CircleShape).clickable {
                    haptic(view)
                    if (tv.isOn) confirmOff = true else Lumen.key("PowerOn")
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.PowerSettingsNew, "Power", tint = Color.White) }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            painting.title + (weather?.let { " · " + it.line(settings.fahrenheit) } ?: ""),
            style = T.italic(15, Color.White.copy(alpha = 0.92f)),
        )
        Spacer(Modifier.height(22.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { DPad(press) }
        Spacer(Modifier.height(22.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            NavButton(Icons.AutoMirrored.Rounded.Undo, "Back", painting) { press("Back") }
            NavButton(Icons.Rounded.Home, "Home", painting) { press("Home") }
            NavButton(Icons.Rounded.Star, "Options", painting) { press("Info") }
            NavButton(Icons.Rounded.Keyboard, "Type", painting) { typing = true }
        }
        Spacer(Modifier.height(18.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.height(56.dp).frosted(painting.deep, RoundedCornerShape(28.dp)).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RepeatIcon(Icons.Rounded.FastRewind, "Rewind") { press("Rev") }
                Box(
                    Modifier.size(width = 56.dp, height = 44.dp).clip(RoundedCornerShape(22.dp)).background(Color.White)
                        .clickable { press("Play") },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.PlayArrow, "Play or pause", tint = Ink.ink) }
                RepeatIcon(Icons.Rounded.FastForward, "Fast forward") { press("Fwd") }
            }
            if (tv.info?.isTv != false) {
                Row(
                    Modifier.weight(1f).height(56.dp).frosted(painting.deep, RoundedCornerShape(28.dp)).padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    RepeatIcon(Icons.Rounded.Remove, "Volume down") { press("VolumeDown") }
                    Text("VOL", style = T.ui(13, FontWeight.ExtraBold, Color.White))
                    RepeatIcon(Icons.Rounded.Add, "Volume up") { press("VolumeUp") }
                }
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable { press("VolumeMute") },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Rounded.VolumeOff, "Mute", tint = Color.White) }
            }
        }
        Spacer(Modifier.height(22.dp))
        ZeusButton(onZeus)
        Spacer(Modifier.height(10.dp))
        Text(
            "Swipe the pad to move · tap for OK",
            style = T.ui(13, FontWeight.SemiBold, Color.White.copy(alpha = 0.7f)),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(120.dp))
    }

    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text("Turn TV off?", style = T.title(24, Ink.ink)) },
            confirmButton = { TextButton({ confirmOff = false; Lumen.key("PowerOff") }) { Text("Turn off", style = T.ui(15, FontWeight.ExtraBold, Ink.ink)) } },
            dismissButton = { TextButton({ confirmOff = false }) { Text("Cancel", style = T.ui(15, FontWeight.Bold, Ink.ink60)) } },
            containerColor = Ink.paper,
        )
    }
    if (typing) TypeSheet { typing = false }
}

/** 280dp paper disc, 112dp yellow OK with the dashed Zeus ring. Tap, swipe, or hold an arrow to repeat. */
@Composable
private fun DPad(press: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val centerR = with(density) { 56.dp.toPx() }
    val swipe = with(density) { 44.dp.toPx() }
    fun dir(d: Offset) = if (abs(d.x) > abs(d.y)) (if (d.x > 0) "Right" else "Left") else (if (d.y > 0) "Down" else "Up")

    Box(
        Modifier
            .size(280.dp)
            .shadow(18.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(Ink.paper)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val rel = down.position - c
                    val inCenter = rel.getDistance() < centerR
                    var origin = down.position
                    var swiped = false
                    var repeat: Job? = null
                    if (!inCenter) {
                        val k = dir(rel)
                        press(k)
                        repeat = scope.launch { delay(400); while (true) { press(k); delay(150) } }
                    }
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                        val d = ch.position - origin
                        if (d.getDistance() > swipe) {
                            repeat?.cancel()
                            swiped = true
                            press(dir(d))
                            origin = ch.position
                        }
                    }
                    repeat?.cancel()
                    if (inCenter && !swiped) press("Select")
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Chevron(Icons.Rounded.KeyboardArrowUp, Modifier.align(Alignment.TopCenter).offset(y = 24.dp))
        Chevron(Icons.Rounded.KeyboardArrowDown, Modifier.align(Alignment.BottomCenter).offset(y = (-24).dp))
        Chevron(Icons.Rounded.KeyboardArrowLeft, Modifier.align(Alignment.CenterStart).offset(x = 24.dp))
        Chevron(Icons.Rounded.KeyboardArrowRight, Modifier.align(Alignment.CenterEnd).offset(x = (-24).dp))
        Box(
            Modifier.size(112.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(Ink.yellow).border(3.dp, Ink.ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(84.dp)) {
                drawCircle(
                    Ink.ink, radius = size.minDimension / 2 - 2.dp.toPx(),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 9.dp.toPx()))),
                )
            }
            Text("OK", style = T.ui(20, FontWeight.ExtraBold, Ink.ink))
        }
    }
}

@Composable
private fun Chevron(icon: ImageVector, modifier: Modifier) {
    Icon(icon, null, tint = Ink.ink, modifier = modifier.size(36.dp))
}

@Composable
private fun NavButton(icon: ImageVector, label: String, painting: Painting, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).frosted(painting.deep, CircleShape, 0.35f).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = T.ui(12, FontWeight.Bold, Color.White.copy(alpha = 0.9f)))
    }
}

/** Sends once on press, then repeats every 150ms after 400ms while held. */
@Composable
private fun RepeatIcon(icon: ImageVector, label: String, size: Dp = 44.dp, onPress: () -> Unit) {
    val scope = rememberCoroutineScope()
    Box(
        Modifier.size(size).clip(CircleShape).pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                onPress()
                val job = scope.launch { delay(400); while (true) { onPress(); delay(150) } }
                while (true) {
                    val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) break
                }
                job.cancel()
            }
        },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = Color.White) }
}

@Composable
private fun ZeusButton(onZeus: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(42.dp)).background(Ink.yellow)
            .border(2.dp, Ink.ink, RoundedCornerShape(42.dp)).clickable(onClick = onZeus).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZeusStar(60.dp, disc = Ink.ink, line = Ink.yellow)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Talk to Zeus", style = T.ui(19, FontWeight.ExtraBold, Ink.ink))
            Text("Tap and say what you want", style = T.ui(13, FontWeight.Bold, Ink.ink.copy(alpha = 0.7f)))
        }
        Box(Modifier.size(52.dp).clip(CircleShape).background(Ink.ink), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Mic, "Talk", tint = Ink.yellow)
        }
    }
}

/** Type: streams Lit_ keypresses as you type, Backspace on delete (spec 15.5). */
@Composable
private fun TypeSheet(onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Ink.paper,
        title = { Text("Type on the TV", style = T.title(24, Ink.ink)) },
        text = {
            Column {
                Text("Letters go to the TV as you type.", style = T.ui(14, FontWeight.SemiBold, Ink.ink60))
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { new ->
                        val old = text
                        text = new
                        scope.launch {
                            val ip = Lumen.ip() ?: return@launch
                            val common = old.commonPrefixWith(new).length
                            repeat(old.length - common) { Lumen.ecp.keypress(ip, "Backspace") }
                            new.substring(common).forEach { Lumen.ecp.literal(ip, it) }
                        }
                    },
                    textStyle = T.ui(18, FontWeight.SemiBold, Ink.ink),
                    cursorBrush = SolidColor(Ink.ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White)
                        .padding(16.dp).focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton({ Lumen.key("Select"); onClose() }) { Text("Enter", style = T.ui(15, FontWeight.ExtraBold, Ink.ink)) }
        },
        dismissButton = { TextButton(onClose) { Text("Done", style = T.ui(15, FontWeight.Bold, Ink.ink60)) } },
    )
}
