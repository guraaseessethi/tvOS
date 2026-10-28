package app.lumen.bridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * The Memories card on the TV (PROGRESS, build 21): photos picked on the phone, served by the bridge.
 * Stored as TV-sized JPEGs (max 1280 px) in the app's private files.
 */
class Memories(private val context: Context) {
    val dir = File(context.filesDir, "memories").apply { mkdirs() }
    private val cards = File(context.cacheDir, "memory-cards").apply { mkdirs() }
    private val name = Regex("^[A-Za-z0-9_.-]{1,80}$")

    fun list(): List<File> = dir.listFiles { f -> f.name.endsWith(".jpg") }?.sortedByDescending { it.lastModified() }?.take(200) ?: emptyList()

    fun file(n: String): File? = if (name.matches(n)) File(dir, n).takeIf { it.isFile } else null

    suspend fun add(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = decode(uri) ?: return@runCatching false
            val out = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            true
        }.getOrDefault(false)
    }

    fun delete(f: File) {
        f.delete()
        cards.listFiles()?.filter { it.name.startsWith(f.nameWithoutExtension) }?.forEach { it.delete() }
    }

    private fun decode(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            return ImageDecoder.decodeBitmap(src) { dec, info, _ ->
                val s = info.size
                val scale = 1280.0 / max(s.width, s.height)
                if (scale < 1) dec.setTargetSize((s.width * scale).toInt(), (s.height * scale).toInt())
                dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val scale = 1280.0 / max(bmp.width, bmp.height)
        return if (scale < 1) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
    }

    /** The photo cropped to fill a rounded 576x384 card (PNG), cached. */
    fun card(f: File): ByteArray? {
        val out = File(cards, "${f.nameWithoutExtension}-card.png")
        if (!out.exists()) {
            val bmp = BitmapFactory.decodeFile(f.path) ?: return null
            val w = 576; val h = 384; val r = 68f
            val s = max(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
            val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(Matrix().apply {
                    setScale(s, s)
                    postTranslate((w - bmp.width * s) / 2, (h - bmp.height * s) / 2)
                })
            }
            val card = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(card).drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader })
            FileOutputStream(out).use { card.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return out.readBytes()
    }
}
