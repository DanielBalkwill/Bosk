package app.bosk.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A queued utterance for the TTS engine. Persisted so the queue survives process death. */
@Entity(tableName = "speech_queue")
data class SpeechItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val priority: Int = 3,        // 1 (low) .. 5 (high)
    val sourceNoteId: Long? = null,
    val createdAt: Long = 0,
)
