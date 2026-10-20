package com.generic.mnemos.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A reviewable flashcard tied to a note, carrying its FSRS-4.5 scheduling state.
 *
 * NOTE (deviation from the master spec, per global rule #6): the spec listed SM-2-style
 * `interval` / `ease_factor`; those are replaced by the FSRS canonical `stability` /
 * `difficulty`. [reviewHistory] is a JSON array of past {ratedAt, rating} for the optimiser.
 */
@Entity(
    tableName = "reviews",
    indices = [Index("noteId"), Index("dueDate")],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val front: String,
    val back: String,
    val dueDate: Long,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val lastReview: Long? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
    val aiGenerated: Boolean = false,
    val reviewHistory: String? = null,
)
