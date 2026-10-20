package com.generic.mnemos

import com.generic.mnemos.core.review.Streak
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakTest {
    private val today = 20_000L

    @Test
    fun noReviewsMeansNoStreak() {
        assertEquals(0, Streak.current(emptySet(), today))
        assertEquals(0, Streak.current(setOf(today - 3), today))
    }

    @Test
    fun countsConsecutiveDaysEndingToday() {
        assertEquals(1, Streak.current(setOf(today), today))
        assertEquals(3, Streak.current(setOf(today, today - 1, today - 2), today))
    }

    @Test
    fun gapStopsTheCount() {
        assertEquals(2, Streak.current(setOf(today, today - 1, today - 3), today))
    }

    @Test
    fun yesterdayAnchorsWhenTodayNotYetReviewed() {
        assertEquals(2, Streak.current(setOf(today - 1, today - 2), today))
    }
}
