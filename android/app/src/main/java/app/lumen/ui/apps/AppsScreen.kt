package app.lumen.ui.apps

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SettingsInputHdmi
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lumen.Lumen
import app.lumen.bridge.Memories
import app.lumen.ecp.RokuApp
import app.lumen.theme.Painting
import app.lumen.theme.Pastels
import app.lumen.ui.common.Eyebrow
import app.lumen.ui.common.PaperField
import app.lumen.ui.common.frosted
import app.lumen.ui.common.haptic
import app.lumen.ui.theme.Ink
import app.lumen.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Spec 15.5 Apps tab: apps, inputs, repaint strip, plus the Memories photos the TV shows. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun AppsScreen(painting: Painting, toast: (String) -> Unit) {
    val tv by Lumen.tv.collectAsState()
    val settings by Lumen.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    var renaming by remember { mutableStateOf<RokuApp?>(null) }
    LaunchedEffect(Unit) { if (tv.apps.isEmpty()) Lumen.refreshTv() }

    fun open(app: RokuApp, label: String) {
        haptic(view)
        scope.launch { toast(if (Lumen.launch(app.id)) "Opening $label on the TV" else "Couldn't reach the TV") }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))
        Eyebrow(settings.tvName.ifEmpty { "Roku TV" })
        Text("Apps & inputs", style = T.title(34, Color.White))
        Text("Tap to open on the TV", style = T.ui(15, FontWeight.SemiBold, Color.White.copy(alpha = 0.85f)))
        Spacer(Modifier.height(18.dp))

        val apps = tv.realApps
        if (apps.isEmpty()) {
            Text(
                if (tv.reachable) "Loading your apps…" else "Can't see the TV right now.",
                style = T.ui(15, FontWeight.SemiBold, Color.White.copy(alpha = 0.8f)),
            )
        }
        Grid(apps.size + 1) { i ->
            if (i < apps.size) AppTile(apps[i]) { open(apps[i], apps[i].name) }
            else AddTile { scope.launch { Lumen.launch("dev"); toast("Lumen is open: use Add app on the TV") } }
        }

        if (tv.inputs.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Eyebrow("Inputs")
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tv.inputs.forEach { input ->
                    val label = settings.inputName(input.id, input.name)
                    Row(
                        Modifier.height(52.dp).frosted(painting.deep, RoundedCornerShape(26.dp), 0.45f)
                            .combinedClickable(onClick = { open(input, label) }, onLongClick = { renaming = input })
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (input.id.contains("tuner")) Icons.Rounded.LiveTv else Icons.Rounded.SettingsInputHdmi, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(label, style = T.ui(16, FontWeight.Bold, Color.White))
                    }
                }
            }
            Text("Long-press an input to rename it", style = T.ui(12, FontWeight.SemiBold, Color.White.copy(alpha = 0.6f)), modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.height(26.dp))
        Eyebrow("Repaint the TV")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Painting.entries.forEach { p ->
                val on = p == painting
                Column(Modifier.clickable { haptic(view); scope.launch { Lumen.repaint(p); toast("Repainted: ${p.title}") } }) {
                    Image(
                        painterResource(p.thumb), p.title,
                        Modifier.size(width = 120.dp, height = 70.dp).clip(RoundedCornerShape(14.dp))
                            .let { if (on) it.border(3.dp, Ink.yellow, RoundedCornerShape(14.dp)) else it },
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(p.title, style = T.italic(15, Color.White))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val overriding = settings.overrideKey.isNotEmpty() && settings.overrideUntil > System.currentTimeMillis()
        Text(
            when {
                overriding -> "Showing ${painting.title} until " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(settings.overrideUntil)) + " · tap to follow the weather"
                settings.followWeather -> "Following the weather · tap a painting to override for a while"
                else -> "Tap a painting to repaint the TV"
            },
            style = T.ui(14, FontWeight.SemiBold, Color.White.copy(alpha = 0.8f)),
            modifier = Modifier.clickable(enabled = overriding) { scope.launch { Lumen.clearOverride(); toast("Following the weather") } },
        )

        Spacer(Modifier.height(26.dp))
        MemoriesSection(painting, toast)
        Spacer(Modifier.height(120.dp))
    }

    renaming?.let { input ->
        var name by remember(input.id) { mutableStateOf(settings.inputName(input.id, input.name)) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = Ink.paper,
            title = { Text("Rename input", style = T.title(24, Ink.ink)) },
            text = { PaperField(name, { name = it }, "Name, e.g. PlayStation") },
            confirmButton = {
                TextButton({ scope.launch { Lumen.prefs.setInputName(input.id, name) }; renaming = null }) {
                    Text("Save", style = T.ui(15, FontWeight.ExtraBold, Ink.ink))
                }
            },
            dismissButton = { TextButton({ renaming = null }) { Text("Cancel", style = T.ui(15, FontWeight.Bold, Ink.ink60)) } },
        )
    }
}

/** 3-column grid that lives inside a scrolling Column. */
@Composable
private fun Grid(count: Int, cell: @Composable (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        for (row in 0 until (count + 2) / 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (c in 0 until 3) {
                    val i = row * 3 + c
                    Box(Modifier.weight(1f)) { if (i < count) cell(i) }
                }
            }
        }
    }
}

/** A real app: its logo (keyed like on the TV) straight on a painted pastel card. */
@Composable
private fun AppTile(app: RokuApp, onClick: () -> Unit) {
    val icon = rememberAppIcon(app.id)
    Column(Modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Image(painterResource(Pastels.card(app.id)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (icon != null) Image(icon, app.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else Text(app.name, style = T.ui(14, FontWeight.ExtraBold, Ink.ink), maxLines = 2, modifier = Modifier.padding(8.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(app.name, style = T.ui(13, FontWeight.ExtraBold, Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.5f).drawBehind {
                drawRoundRect(
                    Color.White.copy(alpha = 0.7f), cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
                )
            },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, "Add app", tint = Color.White) }
        Spacer(Modifier.height(6.dp))
        Text("Add app", style = T.ui(13, FontWeight.ExtraBold, Color.White))
    }
}

/** Photos for the TV's Memories card, served to the TV by the bridge. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoriesSection(painting: Painting, toast: (String) -> Unit) {
    val context = LocalContext.current
    val memories = remember { Memories(context) }
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val files = remember(version) { memories.list() }
    var deleting by remember { mutableStateOf<File?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            var n = 0
            uris.forEach { if (memories.add(it)) n++ }
            version++
            toast(if (n == 1) "1 photo added to Memories" else "$n photos added to Memories")
        }
    }

    Eyebrow("Memories on the TV")
    Spacer(Modifier.height(4.dp))
    Text("Photos here play on the Memories card. Long-press one to remove it.", style = T.ui(14, FontWeight.SemiBold, Color.White.copy(alpha = 0.8f)))
    Spacer(Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(84.dp).frosted(painting.deep, RoundedCornerShape(14.dp), 0.45f)
                .clickable { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, "Add photos", tint = Color.White) }
        files.forEach { f -> MemoryThumb(f) { deleting = f } }
    }

    deleting?.let { f ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Ink.paper,
            title = { Text("Remove this photo?", style = T.title(22, Ink.ink)) },
            confirmButton = { TextButton({ memories.delete(f); deleting = null; version++ }) { Text("Remove", style = T.ui(15, FontWeight.ExtraBold, Ink.ink)) } },
            dismissButton = { TextButton({ deleting = null }) { Text("Keep", style = T.ui(15, FontWeight.Bold, Ink.ink60)) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemoryThumb(f: File, onLongPress: () -> Unit) {
    var bmp by remember(f.path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(f.path) {
        bmp = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 8 })?.asImageBitmap()
        }
    }
    Box(Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.1f)).combinedClickable(onClick = {}, onLongClick = onLongPress)) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
