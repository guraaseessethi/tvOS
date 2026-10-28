package app.lumen.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lumen.Lumen
import app.lumen.auth.Account
import app.lumen.games.Entry
import app.lumen.games.Game
import app.lumen.games.Leaderboard
import app.lumen.theme.Painting
import app.lumen.ui.common.Eyebrow
import app.lumen.ui.common.OutlineButton
import app.lumen.ui.common.YellowButton
import app.lumen.ui.common.haptic
import app.lumen.zeus.ActionRunner
import app.lumen.ui.common.frosted
import app.lumen.ui.theme.Ink
import app.lumen.ui.theme.T
import kotlinx.coroutines.launch

/** Leaderboard for the TV's games card. Scores arrive through the bridge while this phone is the helper. */
@Composable
fun GamesScreen(painting: Painting, account: Account?, toast: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var gameId by rememberSaveable { mutableStateOf(Game.Bird.id) }
    var playing by remember { mutableStateOf<Game?>(null) }
    var starting by remember { mutableStateOf(false) }
    val game = Game.of(gameId) ?: Game.Bird
    val entries by remember(game) { Leaderboard.top(context, game) }.collectAsState(initial = null)

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(16.dp))
        Eyebrow("Lumen games")
        Text("Leaderboard", style = T.title(34, Color.White))
        Text(
            "Play on the TV's games card. Every run is saved here under your name.",
            style = T.ui(15, FontWeight.SemiBold, Color.White.copy(alpha = 0.85f)),
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Game.entries.forEach { g ->
                val on = g == game
                Column(
                    Modifier.weight(1f).height(92.dp)
                        .let { if (on) it.clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.92f)) else it.frosted(painting.deep, RoundedCornerShape(22.dp), 0.45f) }
                        .clickable { gameId = g.id }.padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Icon(
                        when (g) { Game.Bird -> Icons.Rounded.Air; Game.Dog -> Icons.Rounded.Pets; Game.Catch -> Icons.Rounded.Star },
                        null, tint = if (on) Ink.ink else Color.White, modifier = Modifier.size(24.dp),
                    )
                    Text(g.title, style = T.ui(15, FontWeight.ExtraBold, if (on) Ink.ink else Color.White), maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(game.blurb, style = T.italic(15, Color.White.copy(alpha = 0.9f)))
        Spacer(Modifier.height(16.dp))

        if (playing == game) {
            Controls(game) { playing = null }
        } else {
            YellowButton("Start ${game.title} on the TV", Modifier.fillMaxWidth()) {
                starting = true
                scope.launch {
                    val ok = ActionRunner.startGame(game.id)
                    starting = false
                    if (ok) playing = game else toast("Couldn't reach the TV")
                }
            }
            if (starting) {
                Spacer(Modifier.height(8.dp))
                Text("Opening Lumen on the TV…", style = T.ui(14, FontWeight.SemiBold, Color.White.copy(alpha = 0.85f)))
            }
        }
        Spacer(Modifier.height(18.dp))

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ink.paper).padding(18.dp)) {
            val list = entries
            when {
                account == null -> Text("Sign in to keep scores and see the leaderboard.", style = T.ui(15, FontWeight.SemiBold, Ink.ink60))
                list == null -> Text("Loading…", style = T.ui(15, FontWeight.SemiBold, Ink.ink60))
                list.isEmpty() -> {
                    Text("No scores yet", style = T.title(24, Ink.ink))
                    Spacer(Modifier.height(4.dp))
                    Text("Be the first: open Lumen, go up to the games card and press OK.", style = T.ui(15, FontWeight.SemiBold, Ink.ink60))
                }
                else -> {
                    val mine = list.indexOfFirst { it.uid == account.uid }
                    if (mine >= 0) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Ink.yellow).border(2.dp, Ink.ink, RoundedCornerShape(18.dp)).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("You're #${mine + 1}", style = T.title(22, Ink.ink), modifier = Modifier.weight(1f))
                            Text("Best ${list[mine].score}", style = T.ui(16, FontWeight.ExtraBold, Ink.ink))
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    list.forEachIndexed { i, e -> Rank(i + 1, e, e.uid == account.uid) }
                }
            }
        }
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun Rank(n: Int, e: Entry, me: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(
                when (n) { 1 -> Ink.yellow; 2 -> Color(0xFFDCD7CA); 3 -> Color(0xFFF0C9A6); else -> Color.Transparent },
            ),
            contentAlignment = Alignment.Center,
        ) { Text("$n", style = T.ui(15, FontWeight.ExtraBold, Ink.ink)) }
        Spacer(Modifier.width(14.dp))
        Text(
            e.name + if (me) " (you)" else "",
            style = T.ui(16, if (me) FontWeight.ExtraBold else FontWeight.SemiBold, Ink.ink),
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text("${e.score}", style = T.title(22, Ink.ink))
    }
}

/**
 * The game pad. Flutter and Pup Run are one-button games (OK flaps / jumps); Star Catcher is
 * steered by holding left or right (ECP keydown/keyup, which the TV sees as press and release).
 */
@Composable
private fun Controls(game: Game, onDone: () -> Unit) {
    val view = LocalView.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Ink.paper).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Playing ${game.title}", style = T.title(24, Ink.ink))
        Text(
            when (game) {
                Game.Bird -> "Tap to flap. Keep tapping to stay between the columns."
                Game.Dog -> "Tap to jump the haystacks."
                Game.Catch -> "Hold left or right to move the basket. Catch stars, dodge ink."
            },
            style = T.ui(14, FontWeight.SemiBold, Ink.ink60),
        )
        Spacer(Modifier.height(16.dp))
        if (game == Game.Catch) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                HoldPad("◀", "Left", Modifier.weight(1f))
                HoldPad("▶", "Right", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            TapPad("Start / again", height = 64) { haptic(view); Lumen.key("Select") }
        } else {
            TapPad(if (game == Game.Bird) "FLAP" else "JUMP", height = 180) { haptic(view); Lumen.key("Select") }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineButton("End run") { Lumen.key("Back") }
            OutlineButton("Done") { Lumen.key("Back"); onDone() }
        }
    }
}

/** Fires on touch-down, not on release, so a flap lands the moment your finger does. */
@Composable
private fun TapPad(label: String, height: Int, onPress: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape(28.dp)).background(Ink.yellow)
            .border(3.dp, Ink.ink, RoundedCornerShape(28.dp))
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(); onPress(); waitForUpOrCancellation() } },
        contentAlignment = Alignment.Center,
    ) { Text(label, style = T.ui(if (height > 100) 34 else 18, FontWeight.ExtraBold, Ink.ink)) }
}

/** Holds a direction down on the TV for as long as your finger stays on it. */
@Composable
private fun HoldPad(label: String, key: String, modifier: Modifier) {
    val view = LocalView.current
    Box(
        modifier.height(150.dp).clip(RoundedCornerShape(28.dp)).background(Ink.ink)
            .pointerInput(key) {
                awaitEachGesture {
                    awaitFirstDown()
                    haptic(view)
                    Lumen.scope.launch { Lumen.ip()?.let { Lumen.ecp.keydown(it, key) } }
                    waitForUpOrCancellation()
                    Lumen.scope.launch { Lumen.ip()?.let { Lumen.ecp.keyup(it, key) } }
                }
            },
        contentAlignment = Alignment.Center,
    ) { Text(label, style = T.ui(40, FontWeight.ExtraBold, Ink.yellow)) }
}
