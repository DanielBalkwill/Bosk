package com.generic.mnemos.core.review

/** Streak maths over the per-day review log (pure, unit-testable). */
object Streak {
    /**
     * Consecutive review days ending today — or ending yesterday, so the streak isn't shown as
     * broken before today's session has happened. 0 when neither today nor yesterday has reviews.
     */
    fun current(days: Set<Long>, today: Long): Int {
        val anchor = when {
            today in days -> today
            (today - 1) in days -> today - 1
            else -> return 0
        }
        var n = 0
        while (anchor - n in days) n++
        return n
    }
}
