package app.lumen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SettingsRemote
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.repeatOnLifecycle
import app.lumen.auth.Auth
import app.lumen.ui.apps.AppsScreen
import app.lumen.ui.auth.AuthScreen
import app.lumen.ui.common.KeyboardDownButton
import app.lumen.ui.common.PaintingBackground
import app.lumen.ui.common.Tab
import app.lumen.ui.common.TabBar
import app.lumen.ui.games.GamesScreen
import app.lumen.ui.onboarding.Onboarding
import app.lumen.ui.remote.RemoteScreen
import app.lumen.ui.setup.SetupScreen
import app.lumen.ui.theme.Ink
import app.lumen.ui.theme.LumenTheme
import app.lumen.ui.theme.T
import app.lumen.ui.zeus.ZeusScreen
import app.lumen.watcher.TvWatcherService
import app.lumen.zeus.WakeWord
import app.lumen.zeus.ZeusLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** Bumped when the QS tile or the notification asks Zeus to listen. */
    private val listenAsk = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.getBooleanExtra(EXTRA_LISTEN, false) == true) listenAsk.value++
        setContent {
            LumenTheme {
                Box(Modifier.fillMaxSize()) {
                    Root(listenAsk)
                    Box(Modifier.align(Alignment.BottomEnd)) { KeyboardDownButton() }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_LISTEN, false)) listenAsk.value++
    }

    companion object {
        const val EXTRA_LISTEN = "listen"
    }
}

private val tabs = listOf(
    Tab("Remote", Icons.Rounded.SettingsRemote),
    Tab("Apps", Icons.Rounded.Apps),
    Tab("Zeus", Icons.Rounded.Mic),
    Tab("Games", Icons.Rounded.EmojiEvents),
    Tab("Setup", Icons.Rounded.Settings),
)

@Composable
private fun Root(listenAsk: MutableStateFlow<Int>) {
    val context = LocalContext.current
    val settings by Lumen.settings.collectAsState()
    val painting by Lumen.painting.collectAsState()
    val account by remember { Auth.accounts(context) }.collectAsState()
    var skipAccount by rememberSaveable { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { Lumen.loadedSettings(); loaded = true }
    if (!loaded) {
        Box(Modifier.fillMaxSize().background(painting.deep))
        return
    }

    when {
        account == null && !skipAccount -> AuthScreen(painting) { skipAccount = true }
        !settings.onboarded || !settings.hasTv -> {
            val scope = rememberCoroutineScope()
            Onboarding(painting, account?.name ?: "") {
                scope.launch {
                    Lumen.prefs.setOnboarded(true)
                    TvWatcherService.start(context)
                }
            }
        }
        else -> Home(listenAsk)
    }
}

@Composable
private fun Home(listenAsk: MutableStateFlow<Int>) {
    val context = LocalContext.current
    val painting by Lumen.painting.collectAsState()
    val account by remember { Auth.accounts(context) }.collectAsState()
    val ask by listenAsk.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var listenRequest by remember { mutableIntStateOf(0) }
    var toast by remember { mutableStateOf<String?>(null) }
    var zeusCommand by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val settings by Lumen.settings.collectAsState()
    val lifeState by lifecycle.currentStateAsState()
    val micBusy by ZeusLog.micBusy.collectAsState()
    val view = LocalView.current

    // "Hey Zeus": only while the app is on screen (spec 15.2), paused while Zeus is busy.
    val wake = remember {
        WakeWord(context) { command ->
            tab = 2
            if (command.isNotBlank()) zeusCommand = command else listenRequest++
        }
    }
    val resumed = lifeState.isAtLeast(Lifecycle.State.RESUMED)
    val micOk = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val wakeOn = settings.heyZeus && resumed && micOk && !micBusy
    DisposableEffect(wakeOn) {
        if (wakeOn) wake.start()
        onDispose { wake.stop() }
    }
    // A phone that lives by the TV and listens for "Hey Zeus" keeps its screen on while the app is open.
    DisposableEffect(settings.heyZeus) {
        view.keepScreenOn = settings.heyZeus
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(Unit) {
        TvWatcherService.start(context)
        launch { Lumen.refreshWeather() }
    }
    // Keep the TV status fresh while the app is on screen (the service polls too).
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            Lumen.refreshTv(withApps = true)
            while (true) {
                delay(4000)
                Lumen.refreshTv(withApps = false)
            }
        }
    }
    LaunchedEffect(ask) {
        if (ask > 0) { tab = 2; listenRequest++ }
    }
    LaunchedEffect(toast) {
        if (toast != null) { delay(2400); toast = null }
    }
    val showToast: (String) -> Unit = { toast = it }

    Box(Modifier.fillMaxSize()) {
        if (tab != 2) PaintingBackground(painting)
        when (tab) {
            0 -> RemoteScreen(painting) { tab = 2; listenRequest++ }
            1 -> AppsScreen(painting, showToast)
            2 -> ZeusScreen(painting, listenRequest, zeusCommand) { zeusCommand = null }
            3 -> GamesScreen(painting, account, showToast)
            else -> SetupScreen(
                painting, account,
                onChangeTv = { scope.launch { Lumen.prefs.setTv("", "", ""); Lumen.prefs.setOnboarded(false) } },
                toast = showToast,
            )
        }
        Box(Modifier.align(Alignment.BottomCenter)) {
            TabBar(tabs, tab, painting.deep, onPaper = tab == 2) { tab = it }
        }
        AnimatedVisibility(
            visible = toast != null, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 12.dp),
        ) {
            // Spec 6.8 toast: ink pill, yellow ExtraBold text.
            Text(
                toast ?: "",
                style = T.ui(15, FontWeight.ExtraBold, Ink.yellow),
                modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(Ink.ink).padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}
