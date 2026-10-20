package com.generic.mnemos.core.tts

import kotlinx.coroutines.flow.StateFlow

/** An item to be spoken. */
data class SpeechItem(
    val text: String,
    val priority: Int = 3,        // 1 (low) .. 5 (high)
    val sourceNoteId: Long? = null,
)

/** Observable playback state for the Now Playing notification + any UI. */
data class TtsState(
    val currentText: String? = null,
    val isPlaying: Boolean = false,
    val queueSize: Int = 0,
)

/**
 * Persistent priority queue of utterances + playback control. Backed by Room so the queue
 * survives process death; drives [PlaybackController]. Lives in the ambient service (CP4).
 */
interface TtsQueueEngine {
    val state: StateFlow<TtsState>

    fun enqueue(item: SpeechItem)
    fun play()
    fun pause()
    fun skip()
    fun stop()
    fun reorder(itemId: Long, newPriority: Int)
}
