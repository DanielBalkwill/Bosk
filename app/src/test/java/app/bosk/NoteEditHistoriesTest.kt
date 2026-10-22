package app.bosk

import app.bosk.core.notes.EditHistory
import app.bosk.core.notes.NoteEditHistories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteEditHistoriesTest {

    @Test
    fun forNoteReturnsTheSameStackPerId() {
        val registry = NoteEditHistories()
        assertSame(registry.forNote(1), registry.forNote(1))
        assertNotSame(registry.forNote(1), registry.forNote(2))
    }

    @Test
    fun externalEditIsOneUndoStep() {
        val registry = NoteEditHistories()
        registry.recordExternalEdit(7, oldBody = "the original body", newBody = "")
        val h = registry.forNote(7)
        assertEquals("", h.current)
        assertTrue(h.canUndo)
        assertEquals("the original body", h.undo())
        assertEquals("", h.redo())
    }

    @Test
    fun externalEditAppendsToAnExistingTrail() {
        val registry = NoteEditHistories()
        val h = registry.forNote(3)
        h.reset("v1")
        h.commit("v2")
        registry.recordExternalEdit(3, oldBody = "v2", newBody = "")
        assertEquals("", h.current)
        assertEquals("v2", h.undo())
        assertEquals("v1", h.undo())
    }

    @Test
    fun externalEditIgnoresUnsavedNotes() {
        val registry = NoteEditHistories()
        registry.recordExternalEdit(0, "a", "b")
        registry.recordExternalEdit(-1, "a", "b")
        // Nothing registered under non-ids; a fresh ask still starts empty.
        assertFalse(registry.forNote(1).canUndo)
    }

    @Test
    fun adoptRegistersANewNotesTrailOnce() {
        val registry = NoteEditHistories()
        val session = EditHistory().apply { reset(""); commit("first draft") }
        registry.adopt(42, session)
        assertSame(session, registry.forNote(42))
        // A later adopt (stale caller) must not displace the registered stack.
        registry.adopt(42, EditHistory())
        assertSame(session, registry.forNote(42))
    }
}
