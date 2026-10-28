package app.lumen.ui.zeus

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.lumen.Lumen
import app.lumen.theme.Painting
import app.lumen.ui.common.Chip
import app.lumen.ui.common.Eyebrow
import app.lumen.ui.common.ZeusStar
import app.lumen.ui.theme.Ink
import app.lumen.ui.theme.T
import app.lumen.zeus.Speech
import app.lumen.zeus.Voice
import app.lumen.zeus.Zeus
import app.lumen.zeus.ZeusLog
import app.lumen.zeus.ZeusSession
import kotlinx.coroutines.launch

private enum class Phase { Idle, Listening, Thinking, Reply }

/**
 * Spec 15.5 Zeus tab. Shows the one conversation the phone and the TV share (ZeusLog), so what you
 * ask on the TV appears here and what you ask here pops up on the TV.
 * listenRequest bumps when another screen asks to start listening; command is text to send right
 * away (from "Hey Zeus, open YouTube").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZeusScreen(painting: Painting, listenRequest: Int, command: String?, onCommandUsed: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tv by Lumen.tv.collectAsState()
    val settings by Lumen.settings.collectAsState()
    val log by ZeusLog.exchanges.collectAsState()

    var phase by remember { mutableStateOf(Phase.Idle) }
    var transcript by remember { mutableStateOf("") }
    var level by remember { mutableFloatStateOf(0f) }
    var chips by remember { mutableStateOf(Zeus.DEFAULT_CHIPS) }
    var typed by remember { mutableStateOf("") }
    val voice = remember { Voice(context) }
    val speaking by voice.speaking.collectAsState()
    val scroll = rememberScrollState()

    fun send(text: String) {
        if (text.isBlank()) return
        transcript = text
        phase = Phase.Thinking
        scope.launch {
            val r = ZeusSession.send(context, text)
            if (r.answer.chips.isNotEmpty()) chips = r.answer.chips
            transcript = ""
            phase = Phase.Reply
            if (Lumen.settings.value.spokenReplies) voice.say(r.answer.reply)
        }
    }

    val speech = remember {
        Speech(
            context,
            onPartial = { transcript = it },
            onLevel = { level = it },
            onFinal = { send(it) },
            onError = { msg -> if (phase == Phase.Listening) { phase = Phase.Idle; transcript = msg } },
        )
    }
    // "Hey Zeus" pauses while this screen listens, thinks or talks.
    LaunchedEffect(phase, speaking) {
        ZeusLog.micBusy.value = phase == Phase.Listening || phase == Phase.Thinking || speaking
    }
    DisposableEffect(Unit) { onDispose { speech.destroy(); voice.shutdown(); ZeusLog.micBusy.value = false } }

    fun listen() {
        voice.stop()
        transcript = ""
        phase = Phase.Listening
        speech.start()
    }

    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) listen() else transcript = "Allow the microphone to talk to Zeus."
    }

    fun startListening() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
        else mic.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(listenRequest) { if (listenRequest > 0) startListening() }
    LaunchedEffect(command) { if (!command.isNullOrBlank()) { send(command); onCommandUsed() } }
    // New messages (from here or from the TV) scroll into view.
    LaunchedEffect(log.size, phase) { scroll.animateScrollTo(scroll.maxValue) }

    Column(Modifier.fillMaxSize().background(Ink.paper)) {
        Header(painting, settings.tvName.ifEmpty { "your TV" }, tv.reachable, phase == Phase.Listening, level)
        Column(
            Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            if (!tv.reachable) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink.yellow).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Can't see the TV. Is it plugged in and on this Wi-Fi?", style = T.ui(14, FontWeight.Bold, Ink.ink), modifier = Modifier.weight(1f))
                    Text("Retry", style = T.ui(14, FontWeight.ExtraBold, Ink.ink), modifier = Modifier.clickable { scope.launch { Lumen.refreshTv() } }.padding(8.dp))
                }
                Spacer(Modifier.height(16.dp))
            }

            if (log.isEmpty() && phase == Phase.Idle && transcript.isEmpty()) {
                Eyebrow("Ask Zeus", color = Ink.ink60)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Open an app, change the volume, set a sleep timer or repaint the TV. " +
                        "What you ask here also shows on the TV, and what you ask on the TV shows here.",
                    style = T.title(22, Ink.ink60),
                )
            }

            log.takeLast(6).forEach { e ->
                Spacer(Modifier.height(14.dp))
                UserBubble(e.user, e.fromTv)
                Spacer(Modifier.height(8.dp))
                ZeusBubble(e.reply, e.receipts)
            }

            if (phase == Phase.Listening || phase == Phase.Thinking || (phase == Phase.Idle && transcript.isNotEmpty())) {
                Spacer(Modifier.height(20.dp))
                Eyebrow(if (phase == Phase.Thinking) "Thinking" else "Listening", color = Ink.ink60)
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        transcript.isNotEmpty() -> "\u201C$transcript${if (phase == Phase.Listening) "…" else ""}\u201D"
                        else -> "…"
                    },
                    style = T.title(28, Ink.ink),
                )
                if (phase == Phase.Thinking) {
                    Spacer(Modifier.height(14.dp))
                    ThinkingDots()
                }
            }

            Spacer(Modifier.height(24.dp))
            Eyebrow("Try", color = Ink.ink60)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                chips.forEach { c -> Chip(c) { send(c) } }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(26.dp)).background(Color.White)
                    .border(1.5.dp, Ink.ink.copy(alpha = 0.12f), RoundedCornerShape(26.dp)).padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (typed.isEmpty()) Text("Or type to Zeus…", style = T.ui(15, FontWeight.SemiBold, Ink.ink.copy(alpha = 0.45f)))
                    BasicTextField(
                        typed, { typed = it },
                        singleLine = true,
                        textStyle = T.ui(15, FontWeight.SemiBold, Ink.ink),
                        cursorBrush = SolidColor(Ink.ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send(typed); typed = "" }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(if (typed.isBlank()) Color.Transparent else Ink.ink)
                        .clickable(enabled = typed.isNotBlank()) { send(typed); typed = "" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = if (typed.isBlank()) Ink.ink60 else Ink.yellow, modifier = Modifier.size(20.dp)) }
            }
            Spacer(Modifier.height(16.dp))
        }
        TalkButton(
            listening = phase == Phase.Listening,
            level = level,
            onDown = { if (phase != Phase.Listening) startListening() },
            onRelease = { held -> if (held) speech.stop() },
        )
        Spacer(Modifier.height(104.dp))
    }
}

/** What you said, in yellow on the right; a small "On the TV" tag when it was asked on the TV. */
@Composable
private fun UserBubble(text: String, fromTv: Boolean) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        if (fromTv) Text("ON THE TV", style = T.eyebrow(10, Ink.ink60), modifier = Modifier.padding(end = 6.dp, bottom = 4.dp))
        Text(
            text,
            style = T.ui(16, FontWeight.Bold, Ink.ink),
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp))
                .background(Ink.yellow).padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun ZeusBubble(reply: String, receipts: List<String>) {
    Row(verticalAlignment = Alignment.Bottom) {
        ZeusStar(32.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            reply,
            style = T.ui(16, FontWeight.SemiBold, Ink.ink),
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 6.dp))
                .background(Color.White).padding(horizontal = 18.dp, vertical = 14.dp),
        )
    }
    receipts.forEach { r ->
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.padding(start = 42.dp).clip(RoundedCornerShape(20.dp)).background(Color.White)
                .border(1.5.dp, Ink.ink.copy(alpha = 0.08f), RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✓", style = T.ui(15, FontWeight.ExtraBold, Ink.okGreen))
            Spacer(Modifier.width(8.dp))
            Text(r, style = T.ui(14, FontWeight.Bold, Ink.ink))
        }
    }
}

@Composable
private fun Header(painting: Painting, tvName: String, connected: Boolean, listening: Boolean, level: Float) {
    Box(Modifier.fillMaxWidth().height(250.dp)) {
        Image(painterResource(painting.sharp), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to painting.deep.copy(alpha = 0.25f), 1f to painting.deep.copy(alpha = 0.55f))))
        Row(
            Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("TALKING TO ${tvName.uppercase()}", style = T.eyebrow(12, Color.White), modifier = Modifier.weight(1f), maxLines = 1)
            Row(
                Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (connected) Ink.green else Ink.amber))
                Spacer(Modifier.width(6.dp))
                Text(if (connected) "Connected" else "Offline", style = T.ui(13, FontWeight.Bold, Color.White))
            }
        }
        Box(Modifier.align(Alignment.Center).padding(top = 28.dp), contentAlignment = Alignment.Center) {
            Rings(listening, level)
            ZeusStar(96.dp)
        }
    }
}

/** Three dashed yellow rings that pulse outward while listening (spec 15.5). */
@Composable
private fun Rings(active: Boolean, level: Float) {
    val t = rememberInfiniteTransition(label = "rings")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "p")
    Canvas(Modifier.size(200.dp)) {
        val base = 48.dp.toPx() + 6.dp.toPx()
        for (i in 0 until 3) {
            val p = if (active) ((phase + i * 0.25f) % 1f) else 0f
            val r = base + i * 14.dp.toPx() + (if (active) p * 18.dp.toPx() else 0f)
            val a = if (active) (1f - p) * (0.55f + 0.45f * level) else 0.85f - i * 0.25f
            drawCircle(
                Ink.yellow.copy(alpha = a.coerceIn(0f, 1f)), radius = r,
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 7.dp.toPx()))),
            )
        }
    }
}

@Composable
private fun ThinkingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    val p by t.animateFloat(0f, 3f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "d")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(3) { i ->
            val on = p.toInt() == i
            Box(Modifier.size(10.dp).clip(CircleShape).background(Ink.ink.copy(alpha = if (on) 1f else 0.25f)))
        }
    }
}

/** Ink button: press to talk, hold and release to send, or tap and just speak (it stops on silence). */
@Composable
private fun TalkButton(listening: Boolean, level: Float, onDown: () -> Unit, onRelease: (held: Boolean) -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(64.dp).clip(RoundedCornerShape(32.dp)).background(Ink.ink)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val start = System.currentTimeMillis()
                    onDown()
                    while (true) {
                        val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!ch.pressed) break
                    }
                    onRelease(System.currentTimeMillis() - start > 600)
                }
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (listening) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(0.4f, 0.8f, 1f, 0.6f, 0.9f, 0.5f, 0.7f).forEach { h ->
                    Box(Modifier.width(4.dp).height((8 + 24 * h * (0.35f + 0.65f * level)).dp).clip(CircleShape).background(Ink.yellow))
                }
            }
            Spacer(Modifier.width(14.dp))
            Text("Release to send", style = T.ui(17, FontWeight.ExtraBold, Ink.yellow))
        } else {
            Icon(Icons.Rounded.Mic, null, tint = Ink.yellow)
            Spacer(Modifier.width(10.dp))
            Text("Hold to talk to Zeus", style = T.ui(17, FontWeight.ExtraBold, Ink.yellow))
        }
    }
}
