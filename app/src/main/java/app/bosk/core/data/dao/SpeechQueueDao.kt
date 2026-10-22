package app.bosk.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.bosk.core.data.entity.SpeechItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeechQueueDao {
    /** Highest priority first, then oldest first — the play order. */
    @Query("SELECT * FROM speech_queue ORDER BY priority DESC, createdAt ASC")
    fun observe(): Flow<List<SpeechItemEntity>>

    @Query("SELECT * FROM speech_queue ORDER BY priority DESC, createdAt ASC LIMIT 1")
    suspend fun head(): SpeechItemEntity?

    @Insert
    suspend fun insert(item: SpeechItemEntity): Long

    @Query("DELETE FROM speech_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE speech_queue SET priority = :priority WHERE id = :id")
    suspend fun setPriority(id: Long, priority: Int)

    @Query("DELETE FROM speech_queue")
    suspend fun clear()
}
