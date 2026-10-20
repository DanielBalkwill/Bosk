package com.generic.mnemos

import com.generic.mnemos.core.commands.VaultActions
import com.generic.mnemos.core.notes.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VaultActionsTest {

    @Test
    fun parseTagArgTagOnlyTargetsActiveNote() {
        assertEquals("biology" to "", VaultActions.parseTagArg("biology"))
    }

    @Test
    fun parseTagArgStripsLeadingHashAndKeepsTitle() {
        assertEquals("important" to "Mitosis", VaultActions.parseTagArg("#important Mitosis"))
    }

    @Test
    fun parseTagArgMultiWordTitle() {
        assertEquals("exam" to "Cell Division Notes", VaultActions.parseTagArg("exam Cell Division Notes"))
    }

    @Test
    fun parseTagArgBlankIsNull() {
        assertNull(VaultActions.parseTagArg("   "))
        assertNull(VaultActions.parseTagArg("#"))
    }

    @Test
    fun addTagIsCaseInsensitiveDedupe() {
        assertEquals(listOf("a", "b"), VaultActions.addTag(listOf("a", "b"), "B"))
        assertEquals(listOf("a", "b", "c"), VaultActions.addTag(listOf("a", "b"), "c"))
    }

    @Test
    fun removeTagIsCaseInsensitive() {
        assertEquals(listOf("a"), VaultActions.removeTag(listOf("a", "B"), "b"))
    }

    @Test
    fun tagCountsAggregatesAndOrders() {
        val notes = listOf(
            Note(id = 1, tags = listOf("bio", "exam")),
            Note(id = 2, tags = listOf("Bio")),     // case-insensitive merge with "bio"
            Note(id = 3, tags = listOf("exam", "history")),
        )
        val counts = VaultActions.tagCounts(notes)
        // bio=2, exam=2, history=1 → count desc, then name asc → bio, exam, history
        assertEquals(listOf("bio" to 2, "exam" to 2, "history" to 1), counts)
    }

    @Test
    fun tagCountsCountsANoteOncePerTag() {
        val notes = listOf(Note(id = 1, tags = listOf("dup", "Dup", " dup ")))
        assertEquals(listOf("dup" to 1), VaultActions.tagCounts(notes))
    }

    @Test
    fun copyTitleSuffixesAndHandlesBlank() {
        assertEquals("Mitosis (copy)", VaultActions.copyTitle("Mitosis"))
        assertEquals("Untitled (copy)", VaultActions.copyTitle(""))
    }

    @Test
    fun statsLinesFormatPercent() {
        val lines = VaultActions.statsLines(total = 40, due = 5, reviewed = 10, masteredFraction = 0.5f)
        assertEquals("cards: 40 total · 5 due", lines[0])
        assertEquals("reviewed (recent sample): 10", lines[1])
        assertEquals("mastered: 50% of sampled cards (stability ≥ 7d)", lines[2])
    }

    @Test
    fun emptyIntentMatchesNaturalAndSlashForms() {
        listOf("empty the note", "Clear it", "/empty", "/wipe", "erase everything", "delete everything")
            .forEach { assert(VaultActions.isEmptyIntent(it)) { "should match: $it" } }
    }

    @Test
    fun emptyIntentIgnoresOrdinaryInstructions() {
        listOf("summarise this note", "add examples", "delete the second paragraph", "/empty Old Draft")
            .forEach { assert(!VaultActions.isEmptyIntent(it)) { "should NOT match: $it" } }
    }
}
