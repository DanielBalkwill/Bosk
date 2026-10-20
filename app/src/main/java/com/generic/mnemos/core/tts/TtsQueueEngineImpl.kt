package com.generic.mnemos.core.tts

import com.generic.mnemos.core.data.dao.SpeechQueueDao
import com.generic.mnemos.core.data.entity.SpeechItemEntity
import com.generic.mnemos.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsQueueEngineImpl @Inject constructor(
    private val dao: SpeechQueueDao,
    private val playback: PlaybackController,
    private val settings: SettingsRepository,
) : TtsQueueEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(TtsState())
    override val state: StateFlow<TtsState> = _state.asStateFlow()

    private var currentId: Long? = null

    init {
        playback.onDone = ::onUtteranceDone
        // Keep the queue-size in state live.
        scope.launch {
            dao.observe().collect { list -> _state.update { it.copy(queueSize = list.size) } }
        }
    }

    override fun enqueue(item: SpeechItem) = launchUnit {
        dao.insert(
            SpeechItemEntity(
                text = item.text,
                priority = item.priority,
                sourceNoteId = item.sourceNoteId,
                createdAt = System.currentTimeMillis(),
            ),
        )
        if (!_state.value.isPlaying) startHead()
    }

    override fun play() = launchUnit { if (!_state.value.isPlaying) startHead() }

    override fun pause() {
        playback.stop()
        _state.update { it.copy(isPlaying = false) }
    }

    override fun skip() = launchUnit {
        playback.stop()
        currentId?.let { dao.deleteById(it) }
        currentId = null
        startHead()
    }

    override fun stop() {
        playback.stop()
        currentId = null
        _state.update { it.copy(isPlaying = false, currentText = null) }
    }

    override fun reorder(itemId: Long, newPriority: Int) = launchUnit {
        dao.setPriority(itemId, newPriority.coerceIn(1, 5))
    }

    private fun onUtteranceDone() = launchUnit {
        currentId?.let { dao.deleteById(it) }
        currentId = null
        startHead()
    }

    /** Speak the highest-priority item, or settle to idle if the queue is empty. */
    private suspend fun startHead() {
        val head = dao.head()
        if (head == null) {
            currentId = null
            _state.update { it.copy(isPlaying = false, currentText = null) }
            return
        }
        currentId = head.id
        val speed = settings.prefs.first().ttsSpeed
        _state.update { it.copy(isPlaying = true, currentText = head.text) }
        playback.speak(head.text, speed)
    }

    private fun launchUnit(block: suspend () -> Unit) { scope.launch { block() } }
}
