package app.bosk

import app.bosk.core.review.QuizletImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quizlet import is parsing-only (no API/scraping), so locking the export-text formats down with
 * unit tests is exactly where the reliability lives.
 */
class QuizletImportTest {

    @Test
    fun parsesDefaultTabAndNewline() {
        val cards = QuizletImport.parse("mitochondria\tpowerhouse of the cell\nosmosis\twater movement")
        assertEquals(2, cards.size)
        assertEquals("mitochondria", cards[0].term)
        assertEquals("powerhouse of the cell", cards[0].definition)
        assertEquals("osmosis", cards[1].term)
    }

    @Test
    fun parsesDashDelimiter() {
        val cards = QuizletImport.parse("TCP - reliable, connection-oriented\nUDP - connectionless")
        assertEquals(2, cards.size)
        assertEquals("TCP", cards[0].term)
        assertEquals("reliable, connection-oriented", cards[0].definition)
    }

    @Test
    fun parsesSemicolonCardSeparatorWhenNoNewlines() {
        val cards = QuizletImport.parse("alpha\tfirst;beta\tsecond")
        assertEquals(2, cards.size)
        assertEquals("beta", cards[1].term)
        assertEquals("second", cards[1].definition)
    }

    @Test
    fun skipsBlankAndSeparatorlessRows() {
        val cards = QuizletImport.parse("good\tdef\n\nnoseparatorhere\nanother\tok")
        assertEquals(2, cards.size)
        assertEquals("good", cards[0].term)
        assertEquals("another", cards[1].term)
    }

    @Test
    fun emptyOrBlankInputYieldsNothing() {
        assertTrue(QuizletImport.parse("").isEmpty())
        assertTrue(QuizletImport.parse("   \n  ").isEmpty())
    }
}
