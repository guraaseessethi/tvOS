package app.lumen.ui.setup

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.lumen.Lumen
import app.lumen.auth.Account
import app.lumen.auth.AppCheck
import app.lumen.auth.Auth
import app.lumen.theme.Painting
import app.lumen.ui.common.CheckRow
import app.lumen.ui.common.Eyebrow
import app.lumen.ui.common.InkButton
import app.lumen.ui.common.OutlineButton
import app.lumen.ui.common.PaperField
import app.lumen.ui.onboarding.askBatteryExemption
import app.lumen.ui.onboarding.batteryExempt
import app.lumen.ui.theme.Ink
import app.lumen.ui.theme.T
import app.lumen.zeus.Voice
import app.lumen.zeus.VoiceStudio
import app.lumen.zeus.ZeusBrain
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Spec 15.5 Setup tab, plus your account. */
@Composable
fun SetupScreen(painting: Painting, account: Account?, onChangeTv: () -> Unit, toast: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tv by Lumen.tv.collectAsState()
    val s by Lumen.settings.collectAsState()
    var battery by remember { mutableStateOf(batteryExempt(context)) }
    var city by remember(s.city) { mutableStateOf(s.city) }
    var name by remember(account?.name) { mutableStateOf(account?.name ?: "") }
    LaunchedEffect(Unit) {
        while (true) { battery = batteryExempt(context); delay(1500) }
    }
    val micForWake = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) scope.launch { Lumen.prefs.setHeyZeus(true) } else toast("Allow the microphone for Hey Zeus")
    }
    var zeusCheck by remember { mutableStateOf<String?>(null) }
    val sample = remember { Voice(context) }
    DisposableEffect(Unit) { onDispose { sample.shutdown() } }
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(16.dp))
            Eyebrow("Lumen for Roku")
            Text("Setup", style = T.title(34, Color.White))
            Spacer(Modifier.height(18.dp))
        }
        Column(
            Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(Ink.paper).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // TV card
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color.White).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(painting.deep), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Tv, null, tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.tvName.ifEmpty { "No TV yet" }, style = T.ui(17, FontWeight.ExtraBold, Ink.ink), maxLines = 1)
                    Text(if (s.tvIp.isNotEmpty()) "${s.tvIp} · on your Wi-Fi" else "Tap Change TV", style = T.ui(13, FontWeight.SemiBold, Ink.ink60))
                }
                Text(
                    if (tv.reachable) "Connected" else "Offline",
                    style = T.ui(14, FontWeight.ExtraBold, if (tv.reachable) Ink.okGreen else Ink.amber),
                )
            }
            Spacer(Modifier.height(8.dp))
            Toggle("Open Lumen when the TV turns on", "Watches for the TV waking up and opens Lumen in about 2 seconds. Backing out of an app returns to Lumen too.", s.autoOpen) {
                scope.launch { Lumen.prefs.setAutoOpen(it) }
            }
            if (s.pauseUntil > System.currentTimeMillis()) {
                Text(
                    "Paused until " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.pauseUntil)) + " · tap to resume",
                    style = T.ui(13, FontWeight.Bold, Ink.amber),
                    modifier = Modifier.clickable { scope.launch { Lumen.prefs.setPauseUntil(0) } }.padding(vertical = 4.dp),
                )
            }
            Divider()
            Toggle("Hey Zeus", "Listens for \"Hey Zeus\" while the Lumen app is open, and keeps the screen on. Good for a phone that lives by the TV.", s.heyZeus) {
                if (it && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    micForWake.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    scope.launch { Lumen.prefs.setHeyZeus(it) }
                }
            }
            Divider()
            Toggle("Spoken replies", "Zeus says his answers out loud.", s.spokenReplies) { scope.launch { Lumen.prefs.setSpokenReplies(it) } }
            Divider()
            Toggle(
                "Zeus answers on the TV",
                "Replies show and play on the TV while Lumen is open. The phone only answers when Lumen isn't on screen.",
                s.replyOnTv,
            ) { scope.launch { Lumen.prefs.setReplyOnTv(it) } }
            Divider()
            // Zeus's voice: the phone's English voices, best first. Tap to hear the next one.
            val voices = remember { VoiceStudio.voices() }
            if (voices.isNotEmpty()) {
                val i = voices.indexOfFirst { it.name == s.voiceName }.coerceAtLeast(0)
                val v = voices[i]
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Zeus's voice", style = T.ui(17, FontWeight.ExtraBold, Ink.ink))
                        Text(
                            "Voice ${i + 1} of ${voices.size} · ${v.locale.displayCountry.ifEmpty { "English" }}" +
                                if (v.quality >= android.speech.tts.Voice.QUALITY_HIGH) " · high quality" else "",
                            style = T.ui(14, FontWeight.SemiBold, Ink.ink60),
                        )
                    }
                    OutlineButton("Try another") {
                        val next = voices[(i + 1) % voices.size]
                        scope.launch { Lumen.prefs.setVoiceName(next.name) }
                        sample.say("Hi, I'm Zeus. Want me to put something on?", next.name)
                    }
                }
            }
            Divider()
            Toggle("Remote on lock screen", "Keeps Play/Pause and a Zeus button in your notifications.", s.lockRemote) { scope.launch { Lumen.prefs.setLockRemote(it) } }
            Divider()
            Toggle("Follow the weather", "The TV's painting changes with the weather outside.", s.followWeather) {
                scope.launch { Lumen.prefs.setFollowWeather(it); Lumen.sendTheme(Lumen.painting.value) }
            }
            Divider()

            Spacer(Modifier.height(10.dp))
            Eyebrow("Checks", color = Ink.ink60)
            CheckRow(tv.mobileControl.takeIf { tv.reachable }, "Roku: Control by mobile apps is on")
            CheckRow(
                when { !tv.reachable -> null; !tv.isOn -> true; else -> null },
                if (tv.reachable && tv.isOn) "Roku: Fast TV start (checked while the TV is off)" else "Roku: Fast TV start is on",
            )
            CheckRow(tv.lumenInstalled, "Lumen is installed on the TV")
            CheckRow(battery, "Battery: allow background use") { askBatteryExemption(context) }

            val last = s.lastAutoOpen.substringBefore("|")
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Ink.yellow).padding(18.dp)) {
                Text(
                    if (last.isNotEmpty()) "Last auto-open: $last." else "Lumen will open by itself the next time the TV turns on.",
                    style = T.ui(15, FontWeight.Bold, Ink.ink),
                )
            }
            val sleepLeft = Lumen.sleepMinutesLeft()
            if (s.sleepAt > System.currentTimeMillis()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Sleep timer: TV off at " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.sleepAt)) + " ($sleepLeft min)",
                        style = T.ui(15, FontWeight.Bold, Ink.ink), modifier = Modifier.weight(1f),
                    )
                    Text("Cancel", style = T.ui(15, FontWeight.ExtraBold, Ink.ink), modifier = Modifier.clickable { scope.launch { Lumen.setSleep(0) } }.padding(6.dp))
                }
            }

            Spacer(Modifier.height(18.dp))
            Eyebrow("Zeus", color = Ink.ink60)
            Spacer(Modifier.height(8.dp))
            OutlineButton(if (zeusCheck == "…") "Checking…" else "Check Zeus") {
                zeusCheck = "…"
                scope.launch { zeusCheck = ZeusBrain.check(context) }
            }
            zeusCheck?.takeIf { it != "…" }?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = T.ui(14, FontWeight.SemiBold, if (it.startsWith("Zeus is working")) Ink.okGreen else Ink.red))
            }
            // App Check (if it's enforced for AI Logic): this phone's debug token, to register once in Firebase.
            val token = remember(zeusCheck) { AppCheck.debugToken(context) }
            if (token != null) {
                Spacer(Modifier.height(10.dp))
                Text("App Check token for this phone", style = T.ui(14, FontWeight.ExtraBold, Ink.ink))
                Text(token, style = T.ui(13, FontWeight.SemiBold, Ink.ink60))
                Spacer(Modifier.height(6.dp))
                OutlineButton("Copy token") {
                    clipboard.setText(AnnotatedString(token))
                    toast("Copied. Add it in Firebase > App Check > Apps > Manage debug tokens")
                }
            }

            Spacer(Modifier.height(18.dp))
            Eyebrow("You", color = Ink.ink60)
            Spacer(Modifier.height(6.dp))
            if (account != null) {
                Text(account.email, style = T.ui(14, FontWeight.SemiBold, Ink.ink60))
                Spacer(Modifier.height(6.dp))
                PaperField(name, { name = it }, "Your name (Zeus and the leaderboard use it)", ime = ImeAction.Done)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton("Save name") {
                        scope.launch { toast(Auth.rename(context, name) ?: "Saved. The TV greets you as ${name.trim()}") }
                    }
                    OutlineButton("Sign out", color = Ink.red) { Auth.signOut(context) }
                }
            } else {
                Text("Not signed in.", style = T.ui(14, FontWeight.SemiBold, Ink.ink60))
            }

            Spacer(Modifier.height(18.dp))
            Eyebrow("Weather", color = Ink.ink60)
            Spacer(Modifier.height(6.dp))
            PaperField(city, { city = it }, "City", ime = ImeAction.Done)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlineButton("Save city") {
                    scope.launch { toast(if (Lumen.setCity(city.trim())) "Weather for ${city.trim()}" else "Couldn't find that city") }
                }
                OutlineButton(if (s.fahrenheit) "°F · switch to °C" else "°C · switch to °F") {
                    scope.launch { Lumen.prefs.setFahrenheit(!s.fahrenheit) }
                }
            }

            Spacer(Modifier.height(18.dp))
            Eyebrow("TV", color = Ink.ink60)
            Spacer(Modifier.height(8.dp))
            InkButton("Change TV", Modifier.fillMaxWidth(), onClick = onChangeTv)
            Spacer(Modifier.height(8.dp))
            Text(
                "This phone is Lumen's helper: keep it on your Wi-Fi and plugged in. It replaces the Mac helper.",
                style = T.ui(13, FontWeight.SemiBold, Ink.ink60),
            )
        }
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun Divider() = HorizontalDivider(color = Ink.ink.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 6.dp))

@Composable
private fun Toggle(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.ui(17, FontWeight.ExtraBold, Ink.ink))
            Text(sub, style = T.ui(14, FontWeight.SemiBold, Ink.ink60))
        }
        Spacer(Modifier.width(14.dp))
        LumenSwitch(on)
    }
}

/** Yellow when on, with an ink-outlined knob (spec 15.5). */
@Composable
private fun LumenSwitch(on: Boolean) {
    val x by animateDpAsState(if (on) 22.dp else 0.dp, label = "knob")
    Box(
        Modifier.size(width = 56.dp, height = 34.dp).clip(RoundedCornerShape(17.dp))
            .background(if (on) Ink.yellow else Color(0xFFDCD7CA))
            .let { if (on) it.border(2.dp, Ink.ink, RoundedCornerShape(17.dp)) else it }
            .padding(3.dp),
    ) {
        Box(
            Modifier.offset(x = x).size(28.dp).clip(CircleShape).background(Color.White)
                .let { if (on) it.border(2.dp, Ink.ink, CircleShape) else it },
        )
    }
}
