package com.generic.mnemos.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.generic.mnemos.core.data.entity.NoteVersionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteVersionDao {
    @Insert
    suspend fun insert(version: NoteVersionEntity): Long

    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY createdAt DESC")
    fun observeForNote(noteId: Long): Flow<List<NoteVersionEntity>>

    @Query("SELECT * FROM note_versions WHERE id = :id")
    suspend fun getById(id: Long): NoteVersionEntity?

    /** Timestamp of the newest snapshot for [noteId] — drives the write throttle. */
    @Query("SELECT MAX(createdAt) FROM note_versions WHERE noteId = :noteId")
    suspend fun latestTimestamp(noteId: Long): Long?

    /** Drop everything but the newest [keep] snapshots of [noteId]. */
    @Query(
        """
        DELETE FROM note_versions WHERE noteId = :noteId AND id NOT IN (
            SELECT id FROM note_versions WHERE noteId = :noteId ORDER BY createdAt DESC LIMIT :keep
        )
        """,
    )
    suspend fun prune(noteId: Long, keep: Int)
}
