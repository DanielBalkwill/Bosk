package com.generic.mnemos.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.generic.mnemos.core.data.entity.ReviewEntity
import kotlinx.coroutines.flow.Flow

/** Projection: a deck (= a note that owns flashcards) with its card counts. */
data class DeckSummaryRow(val noteId: Long, val title: String, val total: Int, val due: Int)

/** Projection: per-note FSRS aggregates — drives the graph's knowledge-health colouring. */
data class NoteHealthRow(val noteId: Long, val total: Int, val due: Int, val avgStability: Double)

@Dao
interface ReviewDao {
    /** Cards due now or earlier, most-overdue first — the review queue. */
    @Query("SELECT * FROM reviews WHERE dueDate <= :now ORDER BY dueDate ASC")
    suspend fun due(now: Long): List<ReviewEntity>

    @Query("SELECT COUNT(*) FROM reviews WHERE dueDate <= :now")
    fun observeDueCount(now: Long): Flow<Int>

    @Query("SELECT * FROM reviews WHERE noteId = :noteId")
    suspend fun forNote(noteId: Long): List<ReviewEntity>

    /** One row per note that has cards: its title, total cards, and how many are due now. */
    @Query(
        """
        SELECT r.noteId AS noteId, n.title AS title, COUNT(*) AS total,
               SUM(CASE WHEN r.dueDate <= :now THEN 1 ELSE 0 END) AS due
        FROM reviews r JOIN notes n ON n.id = r.noteId
        GROUP BY r.noteId, n.title
        ORDER BY n.title
        """,
    )
    suspend fun deckSummaries(now: Long): List<DeckSummaryRow>

    @Query("SELECT * FROM reviews WHERE id = :id")
    suspend fun getById(id: Long): ReviewEntity?

    /** Cards reviewed at least once, most-recently-reviewed first — the progress/history feed. */
    @Query("SELECT * FROM reviews WHERE lastReview IS NOT NULL ORDER BY lastReview DESC LIMIT :limit")
    suspend fun recentlyReviewed(limit: Int): List<ReviewEntity>

    /** Per-note card totals, due counts, and average stability — reactive (graph health mode). */
    @Query(
        """
        SELECT noteId AS noteId, COUNT(*) AS total,
               SUM(CASE WHEN dueDate <= :now THEN 1 ELSE 0 END) AS due,
               AVG(stability) AS avgStability
        FROM reviews
        GROUP BY noteId
        """,
    )
    fun observeNoteHealth(now: Long): Flow<List<NoteHealthRow>>

    @Upsert
    suspend fun upsert(review: ReviewEntity): Long

    @Delete
    suspend fun delete(review: ReviewEntity)

    @Query("SELECT COUNT(*) FROM reviews")
    suspend fun count(): Int
}
