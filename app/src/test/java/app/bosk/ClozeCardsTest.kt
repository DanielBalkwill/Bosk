package app.bosk

import app.bosk.core.review.ClozeCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClozeCardsTest {

    @Test
    fun redactedSpanBecomesClozeCard() {
        val cards = ClozeCards.fromRedactions("The capital of France is ⟦Paris⟧ obviously.")
        assertEquals(1, cards.size)
        assertEquals("Paris", cards[0].back)
        assertTrue(cards[0].front.contains("[…]"))
        assertTrue(cards[0].front.contains("capital of France"))
        assertTrue(!cards[0].front.contains("Paris"))
    }

    @Test
    fun multipleSpansMakeMultipleCardsHidingEachOther() {
        val cards = ClozeCards.fromRedactions("⟦Alpha⟧ pairs with ⟦Beta⟧ here.")
        assertEquals(2, cards.size)
        assertEquals("Alpha", cards[0].back)
        assertEquals("Beta", cards[1].back)
        // The OTHER span stays hidden in each card's context.
        assertTrue(cards[0].front.contains("[redacted]"))
    }

    @Test
    fun blankSpansAreSkippedAndNewlinesFlattened() {
        assertEquals(0, ClozeCards.fromRedactions("nothing ⟦ ⟧ here").size)
        val cards = ClozeCards.fromRedactions("line one\n⟦answer⟧\nline two")
        assertTrue(!cards[0].front.contains("\n"))
    }
}
