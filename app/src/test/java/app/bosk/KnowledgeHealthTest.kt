package app.bosk

import app.bosk.core.review.KnowledgeHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeHealthTest {

    @Test
    fun emptyDeckScoresZero() {
        assertEquals(0f, KnowledgeHealth.score(0, 0, 10.0))
    }

    @Test
    fun allDueLowStabilityIsNearZero() {
        assertTrue(KnowledgeHealth.score(4, 4, 1.0) < 0.1f)
    }

    @Test
    fun noneDueHighStabilityIsNearOne() {
        assertTrue(KnowledgeHealth.score(4, 0, 40.0) > 0.95f)
    }

    @Test
    fun halfDueMidStabilityIsMiddling() {
        val s = KnowledgeHealth.score(4, 2, 15.0)
        assertTrue(s in 0.4f..0.6f)
    }

    @Test
    fun dueCountClampedToTotal() {
        // A malformed row (due > total) must not go negative.
        assertTrue(KnowledgeHealth.score(2, 5, 0.0) >= 0f)
    }
}
