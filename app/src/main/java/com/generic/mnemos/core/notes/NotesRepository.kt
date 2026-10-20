package com.generic.mnemos.core.notes

import kotlinx.coroutines.flow.Flow

/**
 * Note CRUD + markdown + tags + FTS search. Backed by Room NoteEntity (CP2),
 * surfaced by the notes UI (CP3).
 * TODO CP2: back with Room DAO. TODO CP3: inline #tag parsing + FTS4 search.
 */
interface NotesRepository {
    fun observeNotes(): Flow<List<Note>>
    suspend fun get(id: Long): Note?
    suspend fun upsert(note: Note): Long
    /** Move a note to the recycle bin (soft delete — recoverable). */
    suspend fun delete(id: Long)
    suspend fun search(query: String): List<Note>

    // ── Recycle bin ────────────────────────────────────────────────────────────
    /** Notes currently in the recycle bin, most-recently-trashed first. */
    fun observeTrashed(): Flow<List<Note>>
    /** Restore a trashed note back to the live list. */
    suspend fun restore(id: Long)
    /** Permanently remove a single note (no recovery). */
    suspend fun deleteForever(id: Long)
    /** Permanently remove everything in the recycle bin. */
    suspend fun emptyTrash()

    // ── Version history ────────────────────────────────────────────────────────
    /** Historical body snapshots of a note, newest first (written automatically on upsert). */
    fun observeVersions(noteId: Long): Flow<List<NoteVersion>>
}

/** One saved body snapshot in a note's version history. */
data class NoteVersion(
    val id: Long,
    val noteId: Long,
    val title: String,
    val body: String,
    val createdAtMs: Long,
)

/** UI-facing note model (decoupled from the Room entity). */
data class Note(
    val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val tags: List<String> = emptyList(),
    val createdAtMs: Long = 0,
    val updatedAtMs: Long = 0,
    /** Epoch-ms the note was trashed, or null while it's a live note. */
    val deletedAtMs: Long? = null,
)
