package app.lumen.ecp

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.tasks.await

/** Scans the TV's "Connect your phone" QR code with Google's code scanner. */
object QrScan {
    sealed interface Result {
        data class Found(val target: TvQr.Target) : Result
        data class NotLumen(val text: String) : Result
        data object Cancelled : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun scan(context: Context): Result {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        return try {
            val raw = GmsBarcodeScanning.getClient(context, options).startScan().await()?.rawValue
                ?: return Result.Cancelled
            TvQr.parse(raw)?.let { Result.Found(it) } ?: Result.NotLumen(raw)
        } catch (e: kotlinx.coroutines.CancellationException) {
            Result.Cancelled
        } catch (e: Exception) {
            Result.Failed(e.message ?: "The scanner didn't open.")
        }
    }
}
