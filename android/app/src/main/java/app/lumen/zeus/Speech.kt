package app.lumen.zeus

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow

/** Hold-to-talk with live partial results (spec 15.2 speech). Call from the main thread. */
class Speech(context: Context, private val onPartial: (String) -> Unit, private val onLevel: (Float) -> Unit,
             private val onFinal: (String) -> Unit, private val onError: (String) -> Unit) {
    private val recognizer: SpeechRecognizer? =
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    var listening = false
        private set
    private var last = ""

    init {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2) / 12).coerceIn(0f, 1f))
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                listening = false
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        if (last.isNotBlank()) onFinal(last) else onError("I didn't catch that.")
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> onError("Allow the microphone to talk to Zeus.")
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> onError("Speech needs the internet right now.")
                    else -> if (last.isNotBlank()) onFinal(last) else onError("I didn't catch that.")
                }
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (text.isNotBlank()) onFinal(text) else if (last.isNotBlank()) onFinal(last) else onError("I didn't catch that.")
            }
            override fun onPartialResults(partial: Bundle?) {
                partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                    if (it.isNotBlank()) { last = it; onPartial(it) }
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    val available get() = recognizer != null

    fun start() {
        val r = recognizer ?: return onError("Speech recognition isn't available on this phone.")
        last = ""
        listening = true
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Commands are short: stop listening soon after you stop talking.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 500L)
        })
    }

    fun stop() { if (listening) recognizer?.stopListening() }
    fun cancel() { listening = false; recognizer?.cancel() }
    fun destroy() { recognizer?.destroy() }
}

/** Spoken replies (spec 15.2), en-CA when the phone has it. */
class Voice(context: Context) {
    private var ready = false
    /** True while a reply is being read out, so "Hey Zeus" doesn't hear Zeus himself. */
    val speaking = MutableStateFlow(false)
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { speaking.value = true }
            override fun onDone(utteranceId: String?) { speaking.value = false }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { speaking.value = false }
        })
    }

    fun say(text: String, preferredVoice: String = "") {
        if (!ready) return
        VoiceStudio.choose(tts, preferredVoice)
        tts.speak(VoiceStudio.spoken(text), TextToSpeech.QUEUE_FLUSH, null, "zeus")
    }

    fun stop() { tts.stop(); speaking.value = false }
    fun shutdown() = tts.shutdown()
}
