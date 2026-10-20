package com.generic.mnemos

import com.generic.mnemos.core.notes.EditHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {

    @Test
    fun freshHistoryHasNothingToUndoOrRedo() {
        val h = EditHistory()
        h.reset("a")
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
        assertEquals("a", h.current)
    }

    @Test
    fun commitThenUndoRedoWalksStack() {
        val h = EditHistory()
        h.reset("a")
        h.commit("ab")
        h.commit("abc")
        assertTrue(h.canUndo)
        assertFalse(h.canRedo)
        assertEquals("ab", h.undo())
        assertEquals("a", h.undo())
        assertNull(h.undo())          // bottom
        assertEquals("ab", h.redo())
        assertEquals("abc", h.redo())
        assertNull(h.redo())          // top
    }

    @Test
    fun commitIgnoresDuplicateOfCurrent() {
        val h = EditHistory()
        h.reset("a")
        h.commit("a")     // same as current → ignored
        assertFalse(h.canUndo)
        h.commit("b")
        h.commit("b")     // dup → ignored
        assertEquals("a", h.undo())
        assertNull(h.undo())
    }

    @Test
    fun commitAfterUndoDiscardsRedoTail() {
        val h = EditHistory()
        h.reset("a")
        h.commit("b")
        h.commit("c")
        h.undo()          // back to "b"
        assertTrue(h.canRedo)
        h.commit("d")     // new branch from "b"
        assertFalse(h.canRedo)
        assertEquals("b", h.undo())
        assertEquals("a", h.undo())
    }

    @Test
    fun capDropsOldestSnapshots() {
        val h = EditHistory(cap = 3)
        h.reset("0")
        h.commit("1")
        h.commit("2")
        h.commit("3")     // exceeds cap 3 → oldest ("0") dropped
        assertEquals("2", h.undo())
        assertEquals("1", h.undo())
        assertNull(h.undo())   // "0" is gone, floor is "1"
    }
}
