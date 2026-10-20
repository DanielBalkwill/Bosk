package com.generic.mnemos.core.ai

import com.generic.mnemos.core.notes.NotesRepository
import com.generic.mnemos.core.review.ReviewRepository
import com.generic.mnemos.core.srs.Rating
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Updates the [KnowledgeProfile] after a review session (the profile feeds every AI call).
 * Topics are derived from the tags of the reviewed notes; ratings sort them into strong vs
 * weak. Retention is an exponential rolling average so it reflects recent performance.
 */
@Singleton
class KnowledgeProfileUpdater @Inject constructor(
    private val profile: KnowledgeProfileManager,
    private val notes: NotesRepository,
    private val reviews: ReviewRepository,
) {
    data class Outcome(val noteId: Long?, val rating: Rating)

    suspend fun onSessionComplete(outcomes: List<Outcome>) {
        if (outcomes.isEmpty()) return

        val strong = mutableSetOf<String>()
        val weak = mutableSetOf<String>()
        val active = mutableSetOf<String>()
        for (o in outcomes) {
            val tags = o.noteId?.let { notes.get(it)?.tags }.orEmpty()
            active += tags
            when (o.rating) {
                Rating.EASY, Rating.GOOD -> strong += tags
                Rating.HARD, Rating.AGAIN -> weak += tags
            }
        }
        val sessionRetention =
            outcomes.count { it.rating == Rating.GOOD || it.rating == Rating.EASY }.toDouble() / outcomes.size
        val total = reviews.totalCards()

        profile.update { prev ->
            prev.copy(
                activeTopics = (active + prev.activeTopics).distinct().take(MAX),
                // A tag the user nailed shouldn't stay flagged weak.
                strongTopics = (strong + prev.strongTopics).distinct().take(MAX),
                weakTopics = ((weak + prev.weakTopics).distinct() - strong).take(MAX),
                totalCards = total,
                retentionRate =
                    if (prev.retentionRate <= 0.0) sessionRetention
                    else prev.retentionRate * 0.7 + sessionRetention * 0.3,
            )
        }
    }

    private companion object {
        const val MAX = 12
    }
}
