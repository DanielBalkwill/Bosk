package com.generic.mnemos

import com.generic.mnemos.core.notes.WikiLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WikiLinksTest {

    @Test
    fun extractsPlainLinks() {
        assertEquals(listOf("Krebs Cycle", "ATP"), WikiLinks.names("See [[Krebs Cycle]] and [[ATP]]."))
    }

    @Test
    fun recognisesEmbeds() {
        val refs = WikiLinks.refs("intro ![[Glycolysis]] end")
        assertEquals(1, refs.size)
        assertTrue(refs[0].embed)
        assertEquals("Glycolysis", refs[0].name)
    }

    @Test
    fun stripsAliasAndHeading() {
        assertEquals(listOf("Note A"), WikiLinks.names("[[Note A|shown text]]"))
        assertEquals(listOf("Note B"), WikiLinks.names("[[Note B#A Section]]"))
    }

    @Test
    fun deduplicatesCaseInsensitively() {
        assertEquals(listOf("ATP"), WikiLinks.names("[[ATP]] and [[atp]] and [[Atp]]"))
    }

    @Test
    fun ignoresEmptyAndNonLinks() {
        assertTrue(WikiLinks.names("no links here, just [single] brackets").isEmpty())
        assertTrue(WikiLinks.names("[[]]").isEmpty())
    }

    @Test
    fun reportsAccuratePositions() {
        val text = "x [[A]] y"
        val ref = WikiLinks.refs(text).single()
        assertEquals("[[A]]", text.substring(ref.start, ref.end))
        assertFalse(ref.embed)
    }

    private val titles = listOf("ATP", "Krebs Cycle", "Krebs Cycle Overview", "Glycolysis")

    @Test
    fun bareLinkMatchesLongestTitleFirst() {
        assertEquals(listOf("Krebs Cycle Overview"), WikiLinks.bareNames("see !Krebs Cycle Overview now", titles))
    }

    @Test
    fun bareLinkStopsAtBoundary() {
        assertEquals(listOf("Krebs Cycle"), WikiLinks.bareNames("ref !Krebs Cycle.", titles))
        // a letter immediately after the title means it isn't that title
        assertTrue(WikiLinks.bareNames("!ATPase", titles).isEmpty())
    }

    @Test
    fun bareLinkIgnoresBracketEmbed() {
        // `![[Glycolysis]]` is an embed, not a bare link — bareRefs must skip it.
        assertTrue(WikiLinks.bareNames("![[Glycolysis]]", titles).isEmpty())
    }

    @Test
    fun bareLinkReportsPositionsAndCaseInsensitive() {
        val text = "what is !atp?"
        val ref = WikiLinks.bareRefs(text, titles).single()
        assertEquals("ATP", ref.name)
        assertEquals("!atp", text.substring(ref.start, ref.end))
    }

    @Test
    fun bracketQueryDetectsInProgressLink() {
        val text = "see [[Kre"
        assertEquals(4 to "Kre", WikiLinks.bracketActiveQuery(text, text.length))
    }

    @Test
    fun bracketQueryEmptyRightAfterBrackets() {
        val text = "x [["
        assertEquals(2 to "", WikiLinks.bracketActiveQuery(text, text.length))
    }

    @Test
    fun bracketQueryKeepsEmbedBang() {
        // For `![[…` the index is the first `[` so replacing from it preserves the `!`.
        val text = "![[Gly"
        assertEquals(1 to "Gly", WikiLinks.bracketActiveQuery(text, text.length))
    }

    @Test
    fun bracketQueryNullWhenClosedOrAbsent() {
        // A closed link is not in progress; single brackets and plain text don't count.
        val closed = "see [[ATP]]"
        assertEquals(null, WikiLinks.bracketActiveQuery(closed, closed.length))
        assertEquals(null, WikiLinks.bracketActiveQuery("just [single", 12))
        assertEquals(null, WikiLinks.bracketActiveQuery("plain text", 5))
    }

    @Test
    fun bracketQueryStopsAtLineBreak() {
        val text = "[[abc\ndef"
        assertEquals(null, WikiLinks.bracketActiveQuery(text, text.length))
    }

    @Test
    fun bracketQueryInsideClosedLinkReturnsQueryUpToCursor() {
        // Caret inside `[[que]]` (before the closing brackets) — the editor strips the stale
        // `]]` on insert, so the query must still be offered.
        val text = "[[que]]"
        assertEquals(0 to "que", WikiLinks.bracketActiveQuery(text, 5))
    }
}
