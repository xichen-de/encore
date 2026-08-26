package app.encore.french

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsController(context: Context, private val onUnavailable: () -> Unit) : TextToSpeech.OnInitListener {
    private var ready = false
    private var initialized = false
    private var pendingText: String? = null
    private val tts = TextToSpeech(context.applicationContext, this)

    override fun onInit(status: Int) {
        initialized = true
        if (status != TextToSpeech.SUCCESS) { pendingText = null; onUnavailable(); return }
        val result = tts.setLanguage(Locale.FRANCE)
        ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        if (!ready) { pendingText = null; onUnavailable(); return }
        pendingText?.let(::speakNow)
        pendingText = null
    }

    fun speak(french: String) {
        if (!ready) {
            if (initialized) onUnavailable() else pendingText = french
            return
        }
        speakNow(french)
    }

    private fun speakNow(french: String) {
        tts.speak(french, TextToSpeech.QUEUE_FLUSH, null, "encore-card")
    }

    fun close() { tts.stop(); tts.shutdown() }
}
