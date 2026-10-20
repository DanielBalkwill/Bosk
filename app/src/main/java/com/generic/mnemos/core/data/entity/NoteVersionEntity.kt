package com.generic.mnemos.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A historical snapshot of a note's body — the safety net behind "restore previous version".
 * Written centrally by the notes repository whenever a body changes materially (throttled, and
 * pruned to the newest [com.generic.mnemos.core.notes.NotesRepositoryImpl.VERSIONS_KEPT] per
 * note), so AI rewrites, bulk edits, and terminal commands are all covered. Rows cascade away
 * with their note.
 */
@Entity(
    tableName = "note_versions",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId")],
)
data class NoteVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    /** The note's title at snapshot time (titles change too). */
    val title: String,
    /** The body as it was BEFORE the change that triggered this snapshot. */
    val body: String,
    val createdAt: Long,
)
