package com.generic.mnemos.core.srs

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * FSRS-4.5 spaced-repetition scheduler (global rule #6 — FSRS, never SM-2).
 *
 * Implemented as PURE functions ([fsrsSchedule], [retrievability]) with no Android/DB
 * deps, so the whole scheduler is unit-testable in isolation (see FsrsTest).
 *
 * Reference: open-source FSRS-4.5 (ts-fsrs / py-fsrs). 17 default weights, power
 * forgetting curve R(t) = (1 + FACTOR·t/S)^DECAY with DECAY = -0.5, FACTOR = 19/81.
 * At the default 90% request retention the interval equals the stability, by definition.
 */

/** User self-assessment after seeing a card. */
enum class Rating(val value: Int) { AGAIN(1), HARD(2), GOOD(3), EASY(4) }

/** Immutable scheduling state for a single card (mirrors ReviewEntity FSRS fields). */
data class FsrsCard(
    val stability: Double = 0.0,   // days until ~90% retention
    val difficulty: Double = 0.0,  // 1–10
    val dueEpochMs: Long = 0L,
    val lastReviewEpochMs: Long? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
)

/** Tunables. Defaults match the canonical FSRS-4.5 configuration. */
data class FsrsParams(
    val w: DoubleArray = DEFAULT_WEIGHTS,
    val requestRetention: Double = 0.9,
    val maximumIntervalDays: Long = 36_500,
) {
    companion object {
        val DEFAULT_WEIGHTS = doubleArrayOf(
            0.4872, 1.4003, 3.7145, 13.8206, 5.1618, 1.2298, 0.8975, 0.031,
            1.6474, 0.1367, 1.0461, 2.1072, 0.0793, 0.3246, 1.587, 0.2272, 2.8755,
        )
    }

    // data class with an array field — override equals/hashCode to satisfy the contract.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FsrsParams) return false
        return w.contentEquals(other.w) &&
            requestRetention == other.requestRetention &&
            maximumIntervalDays == other.maximumIntervalDays
    }

    override fun hashCode(): Int =
        31 * (31 * w.contentHashCode() + requestRetention.hashCode()) + maximumIntervalDays.hashCode()
}

private const val DECAY = -0.5
private const val FACTOR = 19.0 / 81.0
private const val DAY_MS = 86_400_000.0

/** Probability of recall of a card with [stability] after [elapsedDays]. */
fun retrievability(elapsedDays: Double, stability: Double): Double {
    if (stability <= 0.0) return 0.0
    return (1.0 + FACTOR * elapsedDays / stability).pow(DECAY)
}

/**
 * Pure scheduling step. Given the current [card], the user's [rating], and the current
 * time [nowEpochMs], returns the updated card. No side effects.
 */
fun fsrsSchedule(
    card: FsrsCard,
    rating: Rating,
    nowEpochMs: Long,
    params: FsrsParams = FsrsParams(),
): FsrsCard {
    val w = params.w
    val g = rating.value

    val isFirst = card.reps == 0 || card.lastReviewEpochMs == null

    val newStability: Double
    val newDifficulty: Double
    if (isFirst) {
        newStability = initStability(w, g)
        newDifficulty = initDifficulty(w, g)
    } else {
        val elapsedDays = ((nowEpochMs - card.lastReviewEpochMs!!) / DAY_MS).coerceAtLeast(0.0)
        val r = retrievability(elapsedDays, card.stability)
        newDifficulty = nextDifficulty(w, card.difficulty, g)
        newStability = if (rating == Rating.AGAIN) {
            nextForgetStability(w, card.difficulty, card.stability, r)
        } else {
            nextRecallStability(w, card.difficulty, card.stability, r, g)
        }
    }

    val intervalDays = intervalFor(newStability, params)
    val due = nowEpochMs + intervalDays * DAY_MS.toLong()

    return card.copy(
        stability = newStability,
        difficulty = newDifficulty,
        dueEpochMs = due,
        lastReviewEpochMs = nowEpochMs,
        reps = card.reps + 1,
        lapses = card.lapses + if (rating == Rating.AGAIN) 1 else 0,
    )
}

// --- internal FSRS-4.5 equations ---

private fun clampDifficulty(d: Double) = d.coerceIn(1.0, 10.0)

private fun initStability(w: DoubleArray, g: Int): Double = w[g - 1].coerceAtLeast(0.1)

private fun initDifficulty(w: DoubleArray, g: Int): Double =
    clampDifficulty(w[4] - (g - 3) * w[5])

private fun meanReversion(w: DoubleArray, init: Double, current: Double): Double =
    w[7] * init + (1 - w[7]) * current

private fun nextDifficulty(w: DoubleArray, d: Double, g: Int): Double {
    val next = d - w[6] * (g - 3)
    // revert toward the difficulty anchor of a first "Good" review (w[4] == initDifficulty(3))
    return clampDifficulty(meanReversion(w, w[4], next))
}

private fun nextRecallStability(w: DoubleArray, d: Double, s: Double, r: Double, g: Int): Double {
    val hardPenalty = if (g == Rating.HARD.value) w[15] else 1.0
    val easyBonus = if (g == Rating.EASY.value) w[16] else 1.0
    return s * (1 + exp(w[8]) * (11 - d) * s.pow(-w[9]) *
        (exp((1 - r) * w[10]) - 1) * hardPenalty * easyBonus)
}

private fun nextForgetStability(w: DoubleArray, d: Double, s: Double, r: Double): Double =
    w[11] * d.pow(-w[12]) * ((s + 1).pow(w[13]) - 1) * exp((1 - r) * w[14])

/** Days until the card decays to [FsrsParams.requestRetention], from [stability]. */
private fun intervalFor(stability: Double, params: FsrsParams): Long {
    val raw = (stability / FACTOR) * (params.requestRetention.pow(1.0 / DECAY) - 1.0)
    return raw.roundToLong().coerceIn(1, params.maximumIntervalDays)
}
