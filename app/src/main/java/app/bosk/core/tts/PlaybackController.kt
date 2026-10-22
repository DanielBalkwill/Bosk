package app.bosk.core.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thin wrapper over Android's [TextToSpeech]. Handles async init, sentence chunking
 * (TTS has a ~4k char input cap), speech rate, and audio focus (so it ducks/pauses for
 * calls). [onDone] fires when the whole utterance finishes — the queue engine uses it to
 * advance. Free-tier voices only here; Chatterbox upgrade is a paid-tier stub (CP6).
 */
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    @Volatile private var ready = false
    private var pending: (() -> Unit)? = null
    private val remainingChunks = AtomicInteger(0)

    var onDone: (() -> Unit)? = null

    // Explicit type + init-in-method avoids recursive type inference (the init callback
    // references `tts`, which isn't typed yet if we inline the initializer).
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context) { status -> onTtsInit(status) }
    }

    private fun onTtsInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return
        tts?.language = Locale.getDefault()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finishChunk()
            @Deprecated("required override") override fun onError(utteranceId: String?) { finishChunk() }
            override fun onError(utteranceId: String?, errorCode: Int) = finishChunk()
        })
        pending?.invoke()
        pending = null
    }

    private fun finishChunk() {
        if (remainingChunks.decrementAndGet() <= 0) {
            abandonFocus()
            onDone?.invoke()
        }
    }

    fun speak(text: String, speed: Float = 1.0f) {
        val action = {
            tts?.setSpeechRate(speed.coerceIn(0.5f, 2.0f))
            requestFocus()
            val chunks = chunk(text)
            remainingChunks.set(chunks.size.coerceAtLeast(1))
            chunks.forEachIndexed { i, part ->
                val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                tts?.speak(part, mode, null, "bosk-$i")
            }
        }
        if (ready) action() else pending = action
    }

    fun stop() {
        tts?.stop()
        remainingChunks.set(0)
        abandonFocus()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
    }

    /** Split on sentence boundaries, keeping each chunk under the TTS input cap. */
    private fun chunk(text: String, max: Int = 3500): List<String> {
        val sentences = text.split(Regex("(?<=[.!?])\\s+"))
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (s in sentences) {
            if (sb.isNotEmpty() && sb.length + s.length > max) {
                out.add(sb.toString()); sb.clear()
            }
            if (s.length > max) out.addAll(s.chunked(max)) else sb.append(if (sb.isEmpty()) s else " $s")
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out.ifEmpty { listOf(text) }
    }

    private fun requestFocus() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs).build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }
}
