package app.lumen.zeus

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * "Hey Zeus" while the app is open (spec 15.2: foreground only, off by default). It keeps Android's
 * on-device speech recognition running and looks for the wake phrase, so no extra keys or services
 * are needed. onWake gets what was said after "Hey Zeus" ("" if nothing yet). Main thread only.
 */
class WakeWord(private val context: Context, private val onWake: (command: String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var heard = ""

    fun start() {
        if (running || !SpeechRecognizer.isRecognitionAvailable(context)) return
        running = true
        listen()
    }

    fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private fun listen() {
        if (!running) return
        heard = ""
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        })
    }

    private fun again(delayMs: Long) {
        if (running) main.postDelayed({ listen() }, delayMs)
    }

    private fun woke(command: String) {
        // A short tone so you know Zeus heard you.
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60).startTone(ToneGenerator.TONE_PROP_BEEP2, 120) }
        stop()
        onWake(command)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partial: Bundle?) {
            partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { heard = it }
        }

        override fun onResults(results: Bundle?) {
            val all = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty() + heard
            val command = all.firstNotNullOfOrNull { WakePhrase.after(it) }
            if (command != null) woke(command) else again(150)
        }

        override fun onError(error: Int) {
            val partialCommand = WakePhrase.after(heard)
            if (partialCommand != null) return woke(partialCommand)
            again(
                when (error) {
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> 1500L
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { running = false; 0L }
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> 5000L
                    else -> 200L
                },
            )
        }
    }
}
