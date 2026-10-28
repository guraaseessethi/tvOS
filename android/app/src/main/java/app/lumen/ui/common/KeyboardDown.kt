package app.lumen.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import app.lumen.ui.theme.Ink

/** A round "keyboard down" button that sits just above the keyboard whenever it's open. Put it last in a full-screen Box. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardDownButton() {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    Box(Modifier.imePadding().padding(end = 16.dp, bottom = 10.dp), contentAlignment = Alignment.BottomEnd) {
        AnimatedVisibility(WindowInsets.isImeVisible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier.size(48.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Ink.ink)
                    .border(2.dp, Ink.yellow, CircleShape)
                    .clickable { keyboard?.hide(); focus.clearFocus() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.KeyboardArrowDown, "Hide keyboard", tint = Ink.yellow) }
        }
    }
}
