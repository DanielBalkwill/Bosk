package com.generic.mnemos.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.generic.mnemos.core.data.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    /** Live notes only — trashed notes (deletedAt set) are hidden from the main list. */
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    /** Notes currently in the recycle bin, most-recently-trashed first. */
    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashed(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Upsert
    suspend fun upsert(note: NoteEntity): Long

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Move a note to the recycle bin (soft delete). */
    @Query("UPDATE notes SET deletedAt = :ts WHERE id = :id")
    suspend fun softDelete(id: Long, ts: Long)

    /** Restore a note from the recycle bin back to the live list. */
    @Query("UPDATE notes SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    /** Permanently delete everything in the recycle bin. */
    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    // LIKE fallback — used when the FTS MATCH finds nothing (odd punctuation, mid-word fragments).
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND (title LIKE '%' || :q || '%' OR body LIKE '%' || :q || '%') ORDER BY updatedAt DESC")
    suspend fun search(q: String): List<NoteEntity>

    /** Proper full-text search via the FTS4 index (tokenised, prefix-matchable, fast). */
    @Query(
        """
        SELECT n.* FROM notes n
        JOIN notes_fts f ON n.id = f.rowid
        WHERE n.deletedAt IS NULL AND notes_fts MATCH :match
        ORDER BY n.updatedAt DESC
        """,
    )
    suspend fun searchFts(match: String): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes WHERE deletedAt IS NULL")
    suspend fun count(): Int
}
