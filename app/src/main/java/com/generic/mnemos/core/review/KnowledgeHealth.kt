package com.generic.mnemos.core.review

/** Per-note memory-strength aggregates (from the FSRS card table). */
data class NoteHealth(val total: Int, val due: Int, val avgStability: Double) {
    /** 0..1 memory-strength score — see [KnowledgeHealth.score]. */
    val score: Float get() = KnowledgeHealth.score(total, due, avgStability)
}

/**
 * "The graph IS the curriculum" made literal: a rough, explainable 0..1 memory-strength score for
 * a note's flashcards, used to colour graph nodes (red = decaying, green = solid). Pure so it's
 * unit-testable. Not an exact FSRS retrievability integral — a UI heuristic:
 *  - half the score = how much of the deck is NOT currently due (overdue cards drag it down);
 *  - half = average stability normalised against [STABLE_DAYS] (a month-stable deck ≈ mastered).
 */
object KnowledgeHealth {
    /** Stability (days) treated as "fully solid" for the stability half of the score. */
    const val STABLE_DAYS = 30.0

    fun score(total: Int, due: Int, avgStability: Double): Float {
        if (total <= 0) return 0f
        val notDue = (total - due.coerceIn(0, total)) / total.toFloat()
        val stability = (avgStability / STABLE_DAYS).coerceIn(0.0, 1.0).toFloat()
        return (0.5f * notDue + 0.5f * stability).coerceIn(0f, 1f)
    }
}
