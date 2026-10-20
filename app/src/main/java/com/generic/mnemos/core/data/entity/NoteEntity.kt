package com.generic.mnemos.core.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A single note (markdown body). The atomic unit of knowledge in Mnemos. */
@Entity(tableName = "notes", indices = [Index("updatedAt"), Index("deletedAt")])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val tags: List<String> = emptyList(),
    val srsData: String? = null, // optional denormalised SRS summary (canonical state in ReviewEntity)
    val createdAt: Long,
    val updatedAt: Long,
    // Recycle bin: epoch-ms the note was trashed, or null while it's a live note. Trashed notes are
    // hidden from the main list/search but preserved (graph + body intact) until permanently purged.
    val deletedAt: Long? = null,
)
