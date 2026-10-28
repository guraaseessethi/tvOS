package app.lumen.bridge

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Port of bridge/lumen-img.swift `key`: an app icon becomes a logo on a transparent 576x384 canvas
 * (2x the TV's 288x192 card). Flood-fills the background from the border, feathers the edge, drops
 * edge blobs, trims and centres. mode "mono" draws the logo in ink; "mono-red" keeps strong reds
 * and the light shapes inside them (YouTube's badge). PLAN 6.19.
 */
object IconKeyer {
    private const val INK = 28

    /** The keyed logo for an app, cached by app id and icon size. Owner choice: every logo in ink, except YouTube (837). */
    fun cached(cacheDir: File, id: String, raw: ByteArray): ByteArray? {
        val dir = File(cacheDir, "icons").apply { mkdirs() }
        val f = File(dir, "v5-$id-${raw.size}.png")
        if (f.exists()) return f.readBytes()
        val png = key(raw, if (id == "837") "mono-red" else "mono") ?: return null
        f.writeBytes(png)
        return png
    }

    fun key(src: ByteArray, mode: String): ByteArray? {
        val raw = BitmapFactory.decodeByteArray(src, 0, src.size) ?: return null
        // Upscale first so the cut-out has smooth anti-aliased edges (Roku icons are only ~290x218).
        val factor = if (raw.width * raw.height > 200_000) 2 else 4
        val big = Bitmap.createScaledBitmap(raw, raw.width * factor, raw.height * factor, true)
        val w = big.width
        val h = big.height
        val px = IntArray(w * h)
        big.getPixels(px, 0, w, 0, 0, w, h)
        fun r(i: Int) = (px[i] shr 16) and 0xFF
        fun g(i: Int) = (px[i] shr 8) and 0xFF
        fun b(i: Int) = px[i] and 0xFF
        fun dist(i: Int, br: Double, bg: Double, bb: Double): Double {
            val dr = r(i) - br; val dg = g(i) - bg; val db = b(i) - bb
            return sqrt(dr * dr + dg * dg + db * db)
        }

        // Background = median of the border ring.
        val border = ArrayList<Int>(2 * (w + h))
        for (x in 0 until w) { border += x; border += (h - 1) * w + x }
        for (y in 0 until h) { border += y * w; border += y * w + w - 1 }
        fun median(f: (Int) -> Int) = border.map(f).sorted()[border.size / 2].toDouble()
        val br = median(::r); val bgc = median(::g); val bb = median(::b)

        // Flood fill from the border through pixels close to the background colour.
        val tol = 46.0
        val isBg = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var sp = 0
        for (i in border) if (!isBg[i] && dist(i, br, bgc, bb) < tol) { isBg[i] = true; stack[sp++] = i }
        while (sp > 0) {
            val i = stack[--sp]
            val x = i % w; val y = i / w
            if (x > 0) { val j = i - 1; if (!isBg[j] && dist(j, br, bgc, bb) < tol) { isBg[j] = true; stack[sp++] = j } }
            if (x < w - 1) { val j = i + 1; if (!isBg[j] && dist(j, br, bgc, bb) < tol) { isBg[j] = true; stack[sp++] = j } }
            if (y > 0) { val j = i - w; if (!isBg[j] && dist(j, br, bgc, bb) < tol) { isBg[j] = true; stack[sp++] = j } }
            if (y < h - 1) { val j = i + w; if (!isBg[j] && dist(j, br, bgc, bb) < tol) { isBg[j] = true; stack[sp++] = j } }
        }

        // Alpha: 0 in the background, feathered by colour distance on the boundary, 1 inside.
        val alpha = FloatArray(w * h) { 1f }
        for (i in 0 until w * h) {
            if (isBg[i]) { alpha[i] = 0f; continue }
            val x = i % w; val y = i / w
            val edge = (x > 0 && isBg[i - 1]) || (x < w - 1 && isBg[i + 1]) || (y > 0 && isBg[i - w]) || (y < h - 1 && isBg[i + w])
            if (edge) alpha[i] = ((dist(i, br, bgc, bb) - 12) / 90).coerceIn(0.0, 1.0).toFloat()
        }

        var light = 0.0; var solid = 0.0
        for (i in 0 until w * h) if (alpha[i] > 0.5f) {
            if ((0.2126 * r(i) + 0.7152 * g(i) + 0.0722 * b(i)) / 255 > 0.82) light++
            solid++
        }
        val inkLogo = mode == "mono" || (mode == "color" && solid > 0 && light / solid > 0.45)

        val bgLum = (0.2126 * br + 0.7152 * bgc + 0.0722 * bb) / 255
        if (mode != "mono-red" && (inkLogo || bgLum < 0.25)) {
            for (i in 0 until w * h) if (!isBg[i] && dist(i, br, bgc, bb) < tol) alpha[i] = 0f
        }

        // Drop small blobs touching the icon's border (Netflix's grey corner wedges).
        val seen = BooleanArray(w * h)
        val blob = IntArray(w * h)
        for (start in 0 until w * h) {
            if (alpha[start] <= 0.1f || seen[start]) continue
            var n = 0; sp = 0; var touches = false
            seen[start] = true; stack[sp++] = start
            while (sp > 0) {
                val i = stack[--sp]
                blob[n++] = i
                val x = i % w; val y = i / w
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) touches = true
                if (x > 0 && alpha[i - 1] > 0.1f && !seen[i - 1]) { seen[i - 1] = true; stack[sp++] = i - 1 }
                if (x < w - 1 && alpha[i + 1] > 0.1f && !seen[i + 1]) { seen[i + 1] = true; stack[sp++] = i + 1 }
                if (y > 0 && alpha[i - w] > 0.1f && !seen[i - w]) { seen[i - w] = true; stack[sp++] = i - w }
                if (y < h - 1 && alpha[i + w] > 0.1f && !seen[i + w]) { seen[i + w] = true; stack[sp++] = i + w }
            }
            if (touches && n < w * h / 30) for (k in 0 until n) alpha[blob[k]] = 0f
        }

        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (i in 0 until w * h) if (alpha[i] > 0.1f) {
            val x = i % w; val y = i / w
            minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
        }
        if (maxX < 0) { minX = 0; minY = 0; maxX = w - 1; maxY = h - 1 }

        val cw = maxX - minX + 1
        val ch = maxY - minY + 1
        val out = IntArray(cw * ch)
        for (y in 0 until ch) for (x in 0 until cw) {
            val i = (y + minY) * w + (x + minX)
            val a = alpha[i]
            var rr = r(i); var gg = g(i); var bbb = b(i)
            if (mode == "mono-red") {
                val red = rr > 110 && rr > gg * 1.4 && rr > bbb * 1.4
                val innerLight = dist(i, br, bgc, bb) < tol
                if (!red && !innerLight) { rr = INK; gg = INK; bbb = INK }
            } else if (inkLogo) { rr = INK; gg = INK; bbb = INK }
            out[y * cw + x] = ((a * 255).toInt() shl 24) or (rr shl 16) or (gg shl 8) or bbb
        }
        val logo = Bitmap.createBitmap(out, cw, ch, Bitmap.Config.ARGB_8888)

        val canvasBmp = Bitmap.createBitmap(576, 384, Bitmap.Config.ARGB_8888)
        val s = min(424.0 / cw, 232.0 / ch)
        val dw = (cw * s).toFloat(); val dh = (ch * s).toFloat()
        Canvas(canvasBmp).drawBitmap(
            logo, null, RectF((576 - dw) / 2, (384 - dh) / 2, (576 + dw) / 2, (384 + dh) / 2),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        return ByteArrayOutputStream().use { bos ->
            canvasBmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
            bos.toByteArray()
        }
    }
}
