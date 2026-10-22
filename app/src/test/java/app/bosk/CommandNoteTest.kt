package app.bosk

import app.bosk.core.commands.CommandNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandNoteTest {

    private val commands = CommandNote.parse(CommandNote.DEFAULT)

    @Test
    fun bareSlashShowsEverything() {
        val out = CommandNote.filter(commands, "/", limit = 500)
        assertEquals(commands.size, out.size)
    }

    @Test
    fun nonSlashInputShowsEverything() {
        val out = CommandNote.filter(commands, "quiz me on mitosis", limit = 500)
        assertEquals(commands.size, out.size)
    }

    @Test
    fun prefixMatchRanksFirst() {
        val out = CommandNote.filter(commands, "/sum")
        assertTrue(out.isNotEmpty())
        assertEquals("/summarise", out.first().name)
    }

    @Test
    fun exactNameBeatsLongerPrefixMatches() {
        val out = CommandNote.filter(commands, "/cards")
        assertEquals("/cards", out.first().name)
        // The longer variants still rank (as prefix matches), just after the exact hit.
        assertTrue(out.any { it.name == "/cards:n" })
    }

    @Test
    fun laterWordsFindTheCommandByMeaning() {
        // "make" isn't a command, but "cards" identifies /cards by name.
        val out = CommandNote.filter(commands, "/make cards")
        assertTrue(out.any { it.name.startsWith("/cards") })
    }

    @Test
    fun summaryKeywordsMatchToo() {
        // "flashcard" only appears in summaries (e.g. /card, /cards), never as a name prefix hit.
        val out = CommandNote.filter(commands, "/flashcard")
        assertTrue(out.isNotEmpty())
    }

    @Test
    fun unknownTokenMatchesNothing() {
        val out = CommandNote.filter(commands, "/zzzqqq")
        assertTrue(out.isEmpty())
    }

    @Test
    fun parseFindsEmptyCommandWithSummary() {
        val empty = commands.firstOrNull { it.name == "/empty" }
        assertTrue(empty != null && empty.summary.isNotBlank())
    }
}
