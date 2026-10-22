package app.bosk

import app.bosk.core.notes.Mentions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MentionsTest {

    private val titles = listOf("ATP", "Krebs Cycle", "Krebs Cycle Overview", "Glycolysis")

    @Test
    fun matchesMultiWordTitleLongestFirst() {
        val m = Mentions.find("see #Krebs Cycle Overview now", titles)
        assertEquals(1, m.size)
        assertEquals("Krebs Cycle Overview", m[0].title)
    }

    @Test
    fun matchesShorterTitleWhenBoundaryEnds() {
        val m = Mentions.find("ref #Krebs Cycle.", titles)
        assertEquals("Krebs Cycle", m.single().title)
    }

    @Test
    fun caseInsensitiveAndSpanIncludesHash() {
        val text = "what is #atp?"
        val m = Mentions.find(text, titles).single()
        assertEquals("ATP", m.title)
        assertEquals("#atp", text.substring(m.start, m.end))
    }

    @Test
    fun doesNotMatchAcrossNonBoundary() {
        // "#ATPase" should not resolve to ATP (next char is a letter).
        assertTrue(Mentions.find("#ATPase", titles).isEmpty())
    }

    @Test
    fun activeQueryFindsInProgressMention() {
        val text = "explain #Kre"
        val q = Mentions.activeQuery(text, text.length)!!
        assertEquals("Kre", q.query)
        assertEquals(8, q.start)
    }

    @Test
    fun activeQueryNullAcrossNewlineOrNoHash() {
        assertNull(Mentions.activeQuery("no hash here", 6))
        assertNull(Mentions.activeQuery("#tag\nnext line", 10))
    }

    @Test
    fun suggestionsPrefixBeforeContains() {
        val s = Mentions.suggestions("kr", titles)
        assertEquals("Krebs Cycle", s.first()) // prefix match ranks first
    }
}
