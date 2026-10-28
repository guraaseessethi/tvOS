package app.lumen.ui.onboarding

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.lumen.Lumen
import app.lumen.TvState
import app.lumen.ecp.FoundTv
import app.lumen.ecp.SsdpDiscovery
import app.lumen.theme.Painting
import app.lumen.ui.common.CheckRow
import app.lumen.ui.common.InkButton
import app.lumen.ui.common.OutlineButton
import app.lumen.ui.common.PaintingBackground
import app.lumen.ui.common.PaperField
import app.lumen.ui.common.ZeusMark
import app.lumen.ui.theme.Ink
import app.lumen.ui.theme.T
import kotlinx.coroutines.launch

fun batteryExempt(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

@SuppressLint("BatteryLife")
fun askBatteryExemption(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

fun notificationsAllowed(context: Context) = Build.VERSION.SDK_INT < 33 ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED

/** Spec 15.5 onboarding: 4 paper cards on the painting. */
@Composable
fun Onboarding(painting: Painting, name: String, onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        PaintingBackground(painting, sharpHeight = 480.dp)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) { i ->
                    Box(
                        Modifier.height(8.dp).width(if (i == step) 28.dp else 8.dp).clip(CircleShape)
                            .background(if (i <= step) Ink.yellow else Color.White.copy(alpha = 0.35f)),
                    )
                }
            }
            Spacer(Modifier.height(140.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Ink.paper).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (step) {
                    0 -> Welcome(name) { step = 1 }
                    1 -> FindTv { step = 2 }
                    2 -> TvSettings { step = 3 }
                    else -> Permissions(onDone)
                }
            }
        }
    }
}

@Composable
private fun Welcome(name: String, next: () -> Unit) {
    ZeusMark(64.dp)
    Text(if (name.isNotEmpty()) "Hi $name. Your TV, calmer." else "Your TV, calmer.", style = T.title(32, Ink.ink))
    Text(
        "Lumen turns your Roku into one calm painted screen. This phone becomes its remote, opens Lumen when the TV " +
            "turns on, and is how you talk to Zeus.",
        style = T.ui(16, FontWeight.SemiBold, Ink.ink60),
    )
    InkButton("Continue", Modifier.fillMaxWidth(), onClick = next)
}

@Composable
private fun FindTv(next: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(true) }
    var found by remember { mutableStateOf<List<FoundTv>>(emptyList()) }
    var manual by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        searching = true
        found = SsdpDiscovery(context, Lumen.ecp).search()
        searching = false
    }

    fun pick(ip: String) {
        scope.launch {
            error = null
            val info = Lumen.ecp.deviceInfo(ip)
            if (info == null) {
                error = "No Roku answered at $ip. Check the address and that the phone is on the same Wi-Fi."
                return@launch
            }
            Lumen.prefs.setTv(ip, info.name, info.serial)
            Lumen.refreshTv()
            next()
        }
    }

    Text(if (searching) "Finding your Roku…" else if (found.isEmpty()) "No Roku found yet" else "Pick your TV", style = T.title(30, Ink.ink))
    if (searching) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = Ink.ink, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text("Looking on your Wi-Fi", style = T.ui(15, FontWeight.SemiBold, Ink.ink60))
        }
    }
    found.forEach { tv ->
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White)
                .border(2.dp, Ink.ink.copy(alpha = 0.1f), RoundedCornerShape(22.dp))
                .clickable { pick(tv.ip) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(tv.name, style = T.ui(17, FontWeight.ExtraBold, Ink.ink))
                Text(tv.ip, style = T.ui(14, FontWeight.SemiBold, Ink.ink60))
            }
            Text("Use", style = T.ui(15, FontWeight.ExtraBold, Ink.ink))
        }
    }
    if (!searching) OutlineButton("Search again") { attempt++ }
    Text("Or type the TV's IP address (Roku Settings > Network > About)", style = T.ui(14, FontWeight.SemiBold, Ink.ink60))
    PaperField(manual, { manual = it.trim() }, "e.g. 192.168.2.33", keyboard = KeyboardType.Uri, ime = ImeAction.Done)
    error?.let { Text(it, style = T.ui(14, FontWeight.Bold, Ink.red)) }
    InkButton("Connect", Modifier.fillMaxWidth(), enabled = manual.isNotBlank()) { pick(manual) }
}

@Composable
private fun TvSettings(next: () -> Unit) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<TvState?>(null) }
    var checking by remember { mutableStateOf(false) }
    fun check() {
        scope.launch { checking = true; state = Lumen.refreshTv(); checking = false }
    }
    LaunchedEffect(Unit) { check() }
    val s = state

    Text("Two TV settings", style = T.title(30, Ink.ink))
    Text("On the Roku remote, open Settings and set these once:", style = T.ui(15, FontWeight.SemiBold, Ink.ink60))
    Step(1, "Control by mobile apps", "Settings > System > Advanced system settings > Control by mobile apps > Network access > Permissive")
    Step(2, "Fast TV start", "Settings > System > Power > Fast TV start > On, so the phone can see the TV wake up")
    Step(3, "Lumen channel", "Install lumen.zip from the TV's developer page (you've done this already)")
    CheckRow(s?.mobileControl.takeIf { s?.reachable == true }, "Control by mobile apps")
    CheckRow(if (s?.reachable == true) true else null, "TV answers on the network")
    CheckRow(s?.lumenInstalled, "Lumen is installed on the TV")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlineButton(if (checking) "Checking…" else "Check again") { check() }
    }
    InkButton("Continue", Modifier.fillMaxWidth(), onClick = next)
}

@Composable
private fun Step(n: Int, title: String, path: String) {
    Row {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Ink.yellow), contentAlignment = Alignment.Center) {
            Text("$n", style = T.ui(14, FontWeight.ExtraBold, Ink.ink))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = T.ui(16, FontWeight.ExtraBold, Ink.ink))
            Text(path, style = T.ui(14, FontWeight.SemiBold, Ink.ink60))
        }
    }
}

@Composable
private fun Permissions(onDone: () -> Unit) {
    val context = LocalContext.current
    var notes by remember { mutableStateOf(notificationsAllowed(context)) }
    var battery by remember { mutableStateOf(batteryExempt(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notes = it }
    // Re-check when the user comes back from the battery screen.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            battery = batteryExempt(context)
        }
    }

    Text("Let Lumen open itself", style = T.title(30, Ink.ink))
    Text("Two permissions keep the TV watcher running:", style = T.ui(15, FontWeight.SemiBold, Ink.ink60))
    CheckRow(notes, "Notifications: the watcher needs a small ongoing notification") {
        if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    CheckRow(battery, "Battery: allow background use so Android doesn't stop it") { askBatteryExemption(context) }
    Text("Tap a line to allow it.", style = T.ui(13, FontWeight.SemiBold, Ink.ink60))
    InkButton("Start Lumen", Modifier.fillMaxWidth(), onClick = onDone)
}
