package com.generic.mnemos.core.data.entity

import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 shadow of the notes table (title + body), kept in sync by Room's generated triggers.
 * Powers proper full-text search: tokenised, prefix-matchable, and far faster than `LIKE '%…%'`
 * on a big vault. Queried by joining `notes_fts.rowid` back to `notes.id`.
 */
@Fts4(contentEntity = NoteEntity::class)
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(
    val title: String,
    val body: String,
)
