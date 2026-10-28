package app.lumen.zeus

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale

/**
 * Zeus's voice, rendered on the phone. For "answer on the TV", the reply is synthesized to a WAV
 * that the bridge serves at /voice/<name>.wav and Lumen plays through the TV speakers.
 * The phone's best English voice is used (Google's high-quality voices sound far more human
 * than the TV's own speech), or the one picked in Setup.
 */
object VoiceStudio {
    private var tts: TextToSpeech? = null
    private val ready = CompletableDeferred<Boolean>()
    private lateinit var dir: File

    fun init(context: Context) {
        if (tts != null) return
        dir = File(context.cacheDir, "voice").apply { mkdirs() }
        tts = TextToSpeech(context.applicationContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }
    }

    /** English voices, best first: on-device ones (reliable to render), highest quality, then en-CA/US/GB. */
    fun voices(): List<Voice> {
        val all = runCatching { tts?.voices?.toList() }.getOrNull().orEmpty()
        return all.filter { it.locale.language == "en" && !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedWith(
                compareBy<Voice> { it.isNetworkConnectionRequired }
                    .thenByDescending { it.quality }
                    .thenBy { listOf("CA", "US", "GB", "AU").indexOf(it.locale.country).let { i -> if (i < 0) 9 else i } }
                    .thenBy { it.name },
            )
    }

    fun choose(t: TextToSpeech, preferred: String) {
        val list = voices()
        val v = list.firstOrNull { it.name == preferred } ?: list.firstOrNull()
        if (v != null) runCatching { t.voice = v } else t.language = Locale("en", "CA")
        // A touch slower and warmer than the default reads less like a machine.
        t.setSpeechRate(0.97f)
        t.setPitch(0.96f)
    }

    /** Says [text] on the phone, from anywhere (e.g. a question asked on the TV while the app is closed). */
    suspend fun speak(text: String, preferredVoice: String) {
        val t = tts ?: return
        if (withTimeoutOrNull(3000) { ready.await() } != true) return
        choose(t, preferredVoice)
        t.speak(spoken(text), TextToSpeech.QUEUE_FLUSH, null, "zeus-phone")
    }

    /**
     * Renders [text] for the TV and returns the clip's file name (an .m4a, or the .wav if AAC
     * encoding isn't possible), or null if speech isn't available.
     */
    suspend fun render(text: String, preferredVoice: String): String? = withContext(Dispatchers.IO) { renderNow(text, preferredVoice) }

    private suspend fun renderNow(text: String, preferredVoice: String): String? {
        val t = tts ?: return null
        if (withTimeoutOrNull(3000) { ready.await() } != true) return null
        choose(t, preferredVoice)
        val name = "z${System.currentTimeMillis()}.wav"
        val file = File(dir, name)
        val done = CompletableDeferred<Boolean>()
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { if (utteranceId == name) done.complete(true) }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { if (utteranceId == name) done.complete(false) }
        })
        if (t.synthesizeToFile(spoken(text), Bundle(), file, name) != TextToSpeech.SUCCESS) return null
        val ok = withTimeoutOrNull(6000) { done.await() } == true && file.length() > 1000
        // Keep only the last few clips.
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(10)?.forEach { it.delete() }
        if (!ok) return null
        // Roku plays AAC (.m4a) far more reliably than WAV.
        val m4a = File(dir, name.removeSuffix(".wav") + ".m4a")
        return if (AacEncoder.wavToM4a(file, m4a)) m4a.name else name
    }

    fun file(name: String): File? =
        if (::dir.isInitialized && Regex("^z\\d{6,20}\\.(wav|m4a)$").matches(name)) File(dir, name).takeIf { it.isFile } else null

    /** Small tweaks so speech flows: "10:52" reads as a time, "TV" isn't spelled oddly, etc. */
    fun spoken(text: String): String = text
        .replace(Regex("\\bmin\\b"), "minutes")
        .replace("°", " degrees")
}
