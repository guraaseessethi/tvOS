package app.lumen.ui.apps

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.lumen.Lumen
import app.lumen.bridge.IconKeyer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** App logos keyed the same way the TV shows them (PLAN 6.19), kept in memory once loaded. */
object AppIcons {
    private val memory = ConcurrentHashMap<String, ImageBitmap>()

    suspend fun load(id: String): ImageBitmap? {
        memory[id]?.let { return it }
        val ip = Lumen.ip() ?: return null
        val raw = Lumen.ecp.call(ip, "GET", "/query/icon/$id")
        if (!raw.ok) return null
        return withContext(Dispatchers.Default) {
            val png = IconKeyer.cached(Lumen.context.cacheDir, id, raw.body) ?: raw.body
            BitmapFactory.decodeByteArray(png, 0, png.size)?.asImageBitmap()
        }?.also { memory[id] = it }
    }
}

@Composable
fun rememberAppIcon(id: String): ImageBitmap? {
    var bmp by remember(id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(id) { bmp = AppIcons.load(id) }
    return bmp
}
