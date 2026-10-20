package com.generic.mnemos

import com.generic.mnemos.core.notes.Redaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactionTest {

    @Test
    fun toggleWrapsSelection() {
        val r = Redaction.toggle("my secret stuff", 3, 9) // "secret"
        assertEquals("my ${Redaction.OPEN}secret${Redaction.CLOSE} stuff", r.text)
        assertTrue(Redaction.hasMarkers(r.text))
    }

    @Test
    fun toggleUnwrapsWhenSelectionContainsMarkers() {
        val wrapped = "my ${Redaction.OPEN}secret${Redaction.CLOSE} stuff"
        // select the whole wrapped span (incl. markers) and toggle off
        val r = Redaction.toggle(wrapped, 3, 3 + "secret".length + 2)
        assertEquals("my secret stuff", r.text)
        assertFalse(Redaction.hasMarkers(r.text))
    }

    @Test
    fun forAiReplacesRedactedSpansWithPlaceholder() {
        val text = "before ${Redaction.OPEN}hidden${Redaction.CLOSE} after"
        assertEquals("before [redacted] after", Redaction.forAi(text))
    }

    @Test
    fun stripMarkersKeepsWordsForPreview() {
        val text = "a ${Redaction.OPEN}b${Redaction.CLOSE} c"
        assertEquals("a b c", Redaction.stripMarkers(text))
    }

    @Test
    fun emptySelectionIsNoOp() {
        val r = Redaction.toggle("hello", 2, 2)
        assertEquals("hello", r.text)
    }
}
