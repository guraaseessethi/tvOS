package app.lumen.ui.auth

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lumen.R
import app.lumen.auth.Auth
import app.lumen.theme.Painting
import app.lumen.ui.common.InkButton
import app.lumen.ui.common.PaintingBackground
import app.lumen.ui.common.PaperField
import app.lumen.ui.common.ZeusMark
import app.lumen.ui.theme.Ink
import app.lumen.ui.theme.T
import kotlinx.coroutines.launch

/** Sign up (name, email, password, confirm) or sign in, or Google. Owner request 28 Oct. */
@Composable
fun AuthScreen(painting: Painting, onSkip: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var signUp by rememberSaveable { mutableStateOf(true) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val ready = Auth.ready(context)

    fun submit(block: suspend () -> String?) {
        if (busy) return
        busy = true; error = null; note = null
        scope.launch {
            error = block()?.takeIf { it.isNotEmpty() }
            busy = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        PaintingBackground(painting, sharpHeight = 420.dp)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        ) {
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZeusMark(44.dp)
                Spacer(Modifier.width(12.dp))
                Text("Lumen", style = T.title(34, Color.White))
            }
            Spacer(Modifier.height(10.dp))
            Text("Your TV, calmer.", style = T.title(40, Color.White))
            Text(
                "Remote, Zeus and the game leaderboard, all in one place.",
                style = T.ui(15, FontWeight.SemiBold, Color.White.copy(alpha = 0.85f)),
            )
            Spacer(Modifier.height(24.dp))

            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Ink.paper).padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!ready) {
                    Text("Firebase isn't connected yet", style = T.title(26, Ink.ink))
                    Text(
                        "Accounts, the leaderboard and Zeus need this app's google-services.json from Firebase. " +
                            "Add it to android/app/ and rebuild. Until then you can still use the remote.",
                        style = T.ui(15, FontWeight.SemiBold, Ink.ink60),
                    )
                    InkButton("Use the remote for now", Modifier.fillMaxWidth(), onClick = onSkip)
                    return@Column
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFFEDE8DA)).padding(4.dp),
                ) {
                    listOf(true to "Create account", false to "Sign in").forEach { (isUp, label) ->
                        val on = signUp == isUp
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp))
                                .background(if (on) Ink.ink else Color.Transparent)
                                .clickable { signUp = isUp; error = null; note = null },
                            contentAlignment = Alignment.Center,
                        ) { Text(label, style = T.ui(15, FontWeight.ExtraBold, if (on) Ink.yellow else Ink.ink60)) }
                    }
                }
                if (signUp) {
                    PaperField(name, { name = it }, "Your name")
                }
                PaperField(email, { email = it.trim() }, "Email", keyboard = KeyboardType.Email)
                PaperField(password, { password = it }, "Password", password = true, ime = if (signUp) ImeAction.Next else ImeAction.Done)
                if (signUp) {
                    PaperField(confirm, { confirm = it }, "Confirm password", password = true, ime = ImeAction.Done)
                    if (confirm.isNotEmpty() && confirm != password) {
                        Text("The passwords don't match yet.", style = T.ui(13, FontWeight.Bold, Ink.red))
                    }
                }
                error?.let { Text(it, style = T.ui(14, FontWeight.Bold, Ink.red)) }
                note?.let { Text(it, style = T.ui(14, FontWeight.Bold, Ink.okGreen)) }

                if (busy) {
                    Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Ink.ink, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                    }
                } else {
                    InkButton(if (signUp) "Create account" else "Sign in", Modifier.fillMaxWidth()) {
                        submit { if (signUp) Auth.signUp(name, email, password, confirm) else Auth.signIn(email, password) }
                    }
                }
                if (!signUp) {
                    Text(
                        "Forgot password?",
                        style = T.ui(14, FontWeight.Bold, Ink.ink),
                        modifier = Modifier.align(Alignment.End).clickable {
                            submit {
                                val e = Auth.resetPassword(email)
                                if (e == null) note = "Check your email for a reset link."
                                e
                            }
                        }.padding(4.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(Modifier.weight(1f), color = Ink.ink.copy(alpha = 0.15f))
                    Text("  or  ", style = T.ui(13, FontWeight.Bold, Ink.ink60))
                    HorizontalDivider(Modifier.weight(1f), color = Ink.ink.copy(alpha = 0.15f))
                }
                GoogleButton { submit { Auth.signInWithGoogle(context) } }
                Text(
                    "Your name shows on the leaderboard and Zeus uses it to greet you.",
                    style = T.ui(12, FontWeight.SemiBold, Ink.ink60),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GoogleButton(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp)).background(Color.White)
            .border(2.dp, Ink.ink, RoundedCornerShape(28.dp)).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(R.drawable.ic_google), null, Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text("Continue with Google", style = T.ui(16, FontWeight.ExtraBold, Ink.ink))
    }
}
