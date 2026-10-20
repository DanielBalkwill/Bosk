package com.generic.mnemos.core.review

import com.generic.mnemos.core.data.dao.ReviewDao
import com.generic.mnemos.core.data.entity.ReviewEntity
import com.generic.mnemos.core.srs.FsrsCard
import com.generic.mnemos.core.srs.Rating
import com.generic.mnemos.core.srs.fsrsSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** UI-facing flashcard. */
data class ReviewCard(
    val id: Long,
    val noteId: Long,
    val front: String,
    val back: String,
)

/** A deck = a note that owns flashcards, with its counts (for the Practise deck list). */
data class Deck(val noteId: Long, val title: String, val total: Int, val due: Int)

/** A recently-reviewed card with its FSRS progress stats (for the AI flashcard analyzer). */
data class ReviewProgress(
    val id: Long,
    val noteId: Long,
    val front: String,
    val back: String,
    val stability: Double,
    val difficulty: Double,
    val reps: Int,
    val lapses: Int,
    val lastReviewMs: Long?,
    val dueMs: Long,
)

/** Access to flashcards + their FSRS scheduling. */
interface ReviewRepository {
    fun observeDueCount(): Flow<Int>
    suspend fun dueCards(limit: Int = 30): List<ReviewCard>
    suspend fun addCard(noteId: Long, front: String, back: String, aiGenerated: Boolean): Long
    suspend fun cardCountForNote(noteId: Long): Int
    suspend fun totalCards(): Int

    /** All decks (notes with cards) and their counts. */
    suspend fun decks(): List<Deck>

    /** Every card in a deck (note) — for studying a specific deck on demand. */
    suspend fun cardsForNote(noteId: Long): List<ReviewCard>

    /** Recently-reviewed cards with progress stats, most-recent first (AI analyzer feed). */
    suspend fun recentlyReviewed(limit: Int = 20): List<ReviewProgress>

    /** Apply a rating to a card, advancing its FSRS schedule. Returns the noteId (for profile updates). */
    suspend fun recordReview(reviewId: Long, rating: Rating): Long?

    /** Per-note memory-strength aggregates, reactive — drives the graph's health colouring. */
    fun observeNoteHealth(): Flow<Map<Long, NoteHealth>>
}

@Singleton
class ReviewRepositoryImpl @Inject constructor(
    private val dao: ReviewDao,
    private val settings: com.generic.mnemos.core.settings.SettingsRepository,
) : ReviewRepository {

    override fun observeDueCount(): Flow<Int> = dao.observeDueCount(System.currentTimeMillis())

    override suspend fun dueCards(limit: Int): List<ReviewCard> =
        dao.due(System.currentTimeMillis()).take(limit).map {
            ReviewCard(id = it.id, noteId = it.noteId, front = it.front, back = it.back)
        }

    override suspend fun addCard(noteId: Long, front: String, back: String, aiGenerated: Boolean): Long =
        dao.upsert(
            ReviewEntity(
                noteId = noteId,
                front = front.trim(),
                back = back.trim(),
                dueDate = System.currentTimeMillis(),
                aiGenerated = aiGenerated,
            ),
        )

    override suspend fun cardCountForNote(noteId: Long): Int = dao.forNote(noteId).size

    override suspend fun totalCards(): Int = dao.count()

    override suspend fun decks(): List<Deck> =
        dao.deckSummaries(System.currentTimeMillis()).map {
            Deck(noteId = it.noteId, title = it.title, total = it.total, due = it.due)
        }

    override suspend fun cardsForNote(noteId: Long): List<ReviewCard> =
        dao.forNote(noteId).map { ReviewCard(id = it.id, noteId = it.noteId, front = it.front, back = it.back) }

    override suspend fun recentlyReviewed(limit: Int): List<ReviewProgress> =
        dao.recentlyReviewed(limit).map {
            ReviewProgress(
                id = it.id,
                noteId = it.noteId,
                front = it.front,
                back = it.back,
                stability = it.stability,
                difficulty = it.difficulty,
                reps = it.reps,
                lapses = it.lapses,
                lastReviewMs = it.lastReview,
                dueMs = it.dueDate,
            )
        }

    override fun observeNoteHealth(): Flow<Map<Long, NoteHealth>> =
        dao.observeNoteHealth(System.currentTimeMillis()).map { rows ->
            rows.associate { it.noteId to NoteHealth(it.total, it.due, it.avgStability) }
        }

    override suspend fun recordReview(reviewId: Long, rating: Rating): Long? {
        val entity = dao.getById(reviewId) ?: return null
        val card = FsrsCard(
            stability = entity.stability,
            difficulty = entity.difficulty,
            dueEpochMs = entity.dueDate,
            lastReviewEpochMs = entity.lastReview,
            reps = entity.reps,
            lapses = entity.lapses,
        )
        val updated = fsrsSchedule(card, rating, System.currentTimeMillis())
        dao.upsert(
            entity.copy(
                stability = updated.stability,
                difficulty = updated.difficulty,
                dueDate = updated.dueEpochMs,
                lastReview = updated.lastReviewEpochMs,
                reps = updated.reps,
                lapses = updated.lapses,
            ),
        )
        // Tick today's activity counter (streak + stats). Never let bookkeeping fail a review.
        runCatching { settings.bumpReviewCount(java.time.LocalDate.now().toEpochDay()) }
        return entity.noteId
    }
}
