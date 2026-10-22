package app.bosk

import app.bosk.core.srs.FsrsCard
import app.bosk.core.srs.Rating
import app.bosk.core.srs.fsrsSchedule
import app.bosk.core.srs.retrievability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsTest {

    private val now = 1_700_000_000_000L
    private val dayMs = 86_400_000L

    private fun firstReview(rating: Rating) = fsrsSchedule(FsrsCard(), rating, now)

    @Test
    fun firstReview_initialStabilityIncreasesWithRating() {
        val again = firstReview(Rating.AGAIN).stability
        val hard = firstReview(Rating.HARD).stability
        val good = firstReview(Rating.GOOD).stability
        val easy = firstReview(Rating.EASY).stability
        assertTrue("AGAIN<HARD", again < hard)
        assertTrue("HARD<GOOD", hard < good)
        assertTrue("GOOD<EASY", good < easy)
    }

    @Test
    fun firstReview_setsBookkeeping() {
        val c = firstReview(Rating.GOOD)
        assertEquals(1, c.reps)
        assertEquals(now, c.lastReviewEpochMs)
        assertTrue("due is in the future", c.dueEpochMs > now)
    }

    @Test
    fun again_incrementsLapses_recallDoesNot() {
        assertEquals(1, firstReview(Rating.AGAIN).lapses)
        assertEquals(0, firstReview(Rating.GOOD).lapses)
    }

    @Test
    fun difficulty_staysWithinBounds() {
        // Hammer it with repeated EASY then repeated AGAIN; difficulty must stay in [1,10].
        var c = FsrsCard()
        repeat(10) { c = fsrsSchedule(c, Rating.EASY, c.dueEpochMs) }
        assertTrue(c.difficulty in 1.0..10.0)
        repeat(10) { c = fsrsSchedule(c, Rating.AGAIN, c.dueEpochMs) }
        assertTrue(c.difficulty in 1.0..10.0)
    }

    @Test
    fun higherRating_schedulesFurtherOut() {
        // From the same learned card, GOOD should push the due date out further than AGAIN.
        val learned = fsrsSchedule(FsrsCard(), Rating.GOOD, now)
        val reviewAt = learned.dueEpochMs
        val afterGood = fsrsSchedule(learned, Rating.GOOD, reviewAt)
        val afterAgain = fsrsSchedule(learned, Rating.AGAIN, reviewAt)
        assertTrue(afterGood.dueEpochMs > afterAgain.dueEpochMs)
    }

    @Test
    fun intervalApproximatesStabilityAtDefaultRetention() {
        // By FSRS definition, at 90% request retention the interval (days) ≈ stability.
        val c = fsrsSchedule(FsrsCard(), Rating.GOOD, now)
        val intervalDays = (c.dueEpochMs - now).toDouble() / dayMs
        assertEquals(c.stability, intervalDays, 1.0) // within a day of rounding
    }

    @Test
    fun retrievability_isOneAtZeroAndDecays() {
        assertEquals(1.0, retrievability(0.0, 5.0), 1e-9)
        val early = retrievability(1.0, 5.0)
        val late = retrievability(10.0, 5.0)
        assertTrue(early > late)
        assertTrue(late in 0.0..1.0)
    }
}
