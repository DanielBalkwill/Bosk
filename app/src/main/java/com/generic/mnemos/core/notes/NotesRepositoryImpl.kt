package com.generic.mnemos.core.notes

import com.generic.mnemos.core.cache.SessionCache
import com.generic.mnemos.core.data.dao.NoteDao
import com.generic.mnemos.core.data.dao.NoteVersionDao
import com.generic.mnemos.core.data.entity.NoteEntity
import com.generic.mnemos.core.data.entity.NoteVersionEntity
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed [NotesRepository]. Maps between the DB entity and the UI-facing [Note]. */
@Singleton
class NotesRepositoryImpl @Inject constructor(
    private val dao: NoteDao,
    private val versions: NoteVersionDao,
    private val cache: SessionCache,
) : NotesRepository {

    override fun observeNotes(): Flow<List<Note>> =
        dao.observeAll().map { list -> list.map { it.toNote() } }
            .onEach { cache.putNotes(it) }

    override suspend fun get(id: Long): Note? = dao.getById(id)?.toNote()

    override suspend fun upsert(note: Note): Long {
        val now = System.currentTimeMillis()
        // Version snapshot BEFORE overwriting: because this sits at the single write chokepoint,
        // editor saves, AI rewrites, /bulk, and terminal commands are all covered. Throttled so
        // the 700ms save debounce doesn't spam history: a note gets a new snapshot when its last
        // one is old enough OR the body shrank/grew a lot (an AI rewrite / paste / wipe).
        if (note.id != 0L) {
            val old = dao.getById(note.id)
            if (old != null && old.body != note.body && old.body.isNotBlank()) {
                val last = versions.latestTimestamp(note.id) ?: 0L
                val bigDelta = abs(old.body.length - note.body.length) >= VERSION_DELTA_CHARS
                if (now - last >= VERSION_MIN_INTERVAL_MS || bigDelta) {
                    versions.insert(
                        NoteVersionEntity(noteId = note.id, title = old.title, body = old.body, createdAt = now),
                    )
                    versions.prune(note.id, VERSIONS_KEPT)
                }
            }
        }
        val entity = NoteEntity(
            id = note.id,
            title = note.title,
            body = note.body,
            tags = note.tags,
            createdAt = if (note.createdAtMs == 0L) now else note.createdAtMs,
            updatedAt = now,
            // Preserve trashed state across edits (don't silently resurrect a binned note).
            deletedAt = note.deletedAtMs,
        )
        val rowId = dao.upsert(entity)
        // Room's @Upsert returns the new rowId on INSERT but -1 on UPDATE. For an existing note the
        // real id is the one we already hold — returning -1 made callers (graph sync) treat it as a
        // brand-new note id, creating graph nodes for a non-existent note → FK constraint 787.
        return if (note.id != 0L) note.id else rowId
    }

    // Soft delete: move to the recycle bin rather than dropping the row.
    override suspend fun delete(id: Long) = dao.softDelete(id, System.currentTimeMillis())

    override suspend fun search(query: String): List<Note> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        // FTS first: each word becomes a prefix token ("mito atp" → "mito* atp*"). Fall back to
        // the LIKE scan when the query has no indexable tokens or MATCH finds nothing (fragments,
        // punctuation-heavy queries) so behaviour never regresses.
        val tokens = q.split(Regex("\\s+"))
            .map { t -> t.filter { it.isLetterOrDigit() } }
            .filter { it.isNotBlank() }
        val viaFts = if (tokens.isEmpty()) emptyList()
        else runCatching { dao.searchFts(tokens.joinToString(" ") { "$it*" }) }.getOrDefault(emptyList())
        val rows = viaFts.ifEmpty { dao.search(q) }
        return rows.map { it.toNote() }
    }

    override fun observeTrashed(): Flow<List<Note>> =
        dao.observeTrashed().map { list -> list.map { it.toNote() } }

    override suspend fun restore(id: Long) = dao.restore(id)

    override suspend fun deleteForever(id: Long) = dao.deleteById(id)

    override suspend fun emptyTrash() = dao.emptyTrash()

    override fun observeVersions(noteId: Long): Flow<List<NoteVersion>> =
        versions.observeForNote(noteId).map { list ->
            list.map { NoteVersion(it.id, it.noteId, it.title, it.body, it.createdAt) }
        }

    companion object {
        /** Max snapshots kept per note (oldest pruned). */
        const val VERSIONS_KEPT = 20
        /** Minimum age of the newest snapshot before another routine one is written. */
        const val VERSION_MIN_INTERVAL_MS = 10 * 60 * 1000L
        /** A body-length change this large snapshots immediately (rewrite/paste/wipe). */
        const val VERSION_DELTA_CHARS = 200
    }
}

private fun NoteEntity.toNote() = Note(
    id = id,
    title = title,
    body = body,
    tags = tags,
    createdAtMs = createdAt,
    updatedAtMs = updatedAt,
    deletedAtMs = deletedAt,
)
