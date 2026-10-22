package app.bosk.core.commands

/**
 * The "command note" — a user-editable reference of every slash command Bosk understands. It is
 * stored as plain text (default below) in settings and drives:
 *  • the command palette / dropdown in the AI terminal,
 *  • the `/help` family of commands (which read straight from this text).
 *
 * Keeping it as editable text means power users can add, document or re-word commands without a
 * code change, and `/help` always reflects exactly what's written here.
 */
object CommandNote {

    /** One parsed command entry: its name, the full usage line, a one-line summary, and its section. */
    data class CommandDoc(
        val name: String,
        val usage: String,
        val summary: String,
        val section: String,
    )

    /**
     * Parse the command note into structured docs. A command is any line starting with `/`; its
     * summary is the next non-blank line. Parsing stops at the one-liner quick-reference block.
     */
    fun parse(text: String): List<CommandDoc> {
        val lines = text.lines()
        val out = mutableListOf<CommandDoc>()
        var section = ""
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.contains("QUICK REFERENCE")) break // one-liners past here, not docs
            when {
                line.isEmpty() || line.startsWith("━") -> Unit
                line.startsWith("/") -> {
                    val name = line.substringBefore(' ').trim()
                    val summary = lines.drop(i + 1)
                        .firstOrNull { it.isNotBlank() }
                        ?.trim()
                        ?.takeUnless { it.startsWith("/") || it.startsWith("━") }
                        .orEmpty()
                    out.add(CommandDoc(name = name, usage = line, summary = summary, section = section))
                }
                // An ALL-CAPS line that isn't a command/bar is a section header.
                line == line.uppercase() && line.any { it.isLetter() } -> section = line
            }
            i++
        }
        return out.distinctBy { it.name }
    }

    /** The one-liner quick-reference block (what bare `/help` prints), or a generated fallback. */
    fun quickReference(text: String): String {
        val lines = text.lines()
        val hdr = lines.indexOfFirst { it.contains("QUICK REFERENCE") }
        if (hdr < 0) return parse(text).joinToString(" ") { it.name }
        val refLines = lines.drop(hdr + 1).filter { it.trim().startsWith("/") }
        return if (refLines.isEmpty()) parse(text).joinToString(" ") { it.name }
        else refLines.joinToString("\n") { it.trim() }
    }

    /** The full documentation block for a single command (`/help merge`), or null if not found. */
    fun detail(text: String, name: String): String? {
        val target = if (name.startsWith("/")) name else "/$name"
        val lines = text.lines()
        val idx = lines.indexOfFirst {
            val t = it.trim()
            t == target || t.startsWith("$target ")
        }
        if (idx < 0) return null
        val sb = StringBuilder(lines[idx].trim())
        for (j in (idx + 1) until lines.size) {
            val t = lines[j]
            if (t.isBlank()) break
            if (t.trimStart().startsWith("/")) break // start of the next command
            sb.append('\n').append(t.trim())
        }
        return sb.toString()
    }

    /**
     * Filter + RANK parsed [commands] for a palette as the user types. A leading "/" ranks by
     * relevance to everything typed after it — name prefix first (exact > shorter), then
     * name-contains, then commands whose name or summary matches the other words (so "/make
     * cards" surfaces /cards even though the name doesn't start with "make"). No "/" → all.
     */
    fun filter(commands: List<CommandDoc>, input: String, limit: Int = 30): List<CommandDoc> {
        val q = input.trim()
        if (!q.startsWith("/")) return commands.take(limit)
        val typed = q.removePrefix("/").lowercase()
        if (typed.isBlank()) return commands.take(limit)
        val head = typed.substringBefore(' ')
        val tokens = typed.split(Regex("\\s+")).filter { it.isNotBlank() }
        val scored = commands.mapNotNull { c ->
            val name = c.name.removePrefix("/").lowercase()
            var score = 0
            when {
                name == head -> score += 120
                name.startsWith(head) -> score += 100 - name.length // shorter = closer match
                name.contains(head) -> score += 55
            }
            tokens.forEach { t ->
                if (t != head && t.length >= 3) {
                    if (name.contains(t)) score += 25
                    if (c.summary.contains(t, ignoreCase = true)) score += 12
                }
            }
            if (head.length >= 3 && c.summary.contains(head, ignoreCase = true)) score += 8
            if (score <= 0) null else c to score
        }
        return scored.sortedByDescending { it.second }.map { it.first }.take(limit)
    }

    /** Commands whose section name contains [keyword] (for `/help:ai`, `/help:privacy`). */
    fun helpForSection(text: String, keyword: String): String =
        parse(text)
            .filter { it.section.contains(keyword, ignoreCase = true) }
            .joinToString("\n") { "${it.name} — ${it.summary}" }
            .ifBlank { "No commands found for \"$keyword\"." }

    /** The default command note, shipped on first run and restorable from Settings. */
    val DEFAULT: String = """
/new [title]
  Create a new note with the given title and open it in the editor.
  Example: /new Quantum Mechanics Overview

/open [title]
  Open a note by name.
  Example: /open Mitosis

/delete <title>
  Delete the current note, or a named note. Prompts for confirmation.
  Example: /delete
  Example: /delete Old Draft

/kill <title>
  Quickly delete the current note (or a named one) to the recycle bin — no confirmation.
  Example: /kill
  Example: /kill Old Draft

/rename [new title]
  Rename the current note.
  Example: /rename Cell Division — Full Notes

/archive <title>
  Archive the current note or a named note (removes from main list, preserves in graph).
  Example: /archive

/duplicate <title>
  Duplicate the current note or a named note with a "(copy)" suffix.
  Example: /duplicate

/empty <title>
  Empty the current note's body (or a named note's). Reversible: undo (↶) in the editor toolbar brings it back.
  Alias: /wipe
  Example: /empty
  Example: /empty Old Draft

/undo <title>
  Undo the last body change to the current note (or a named note) — including /empty and /bulk edits.
  Example: /undo

/redo <title>
  Redo a change you just undid on the current note (or a named note).
  Example: /redo

/pin <title>
  Pin a note to the top of the notes list.
  Example: /pin Exam Revision Index

/unpin <title>
  Unpin a pinned note.
  Example: /unpin Exam Revision Index

/tag [tag] <title>
  Add a tag to the current note or a named note.
  Example: /tag biology
  Example: /tag important Mitosis

/untag [tag] <title>
  Remove a tag from the current note or a named note.
  Example: /untag draft

/tags
  List all tags used across the vault with note counts.

/recent <n>
  Show the n most recently edited notes (default 10).
  Example: /recent 5

/find [query]
  Full-text search across all notes. Returns a ranked list.
  Example: /find mitochondria ATP synthesis

/orphans
  List all notes with no incoming or outgoing links.

/broken
  List all [[wiki-links]] that point to notes that do not exist.

/trash
  List the notes currently in the recycle bin.
  Alias: /bin

/restore <title>
  Restore a trashed note from the recycle bin back to the live list.
  Example: /restore Old Draft

/card <front> | <back>
  Create a flashcard on the active note (front and back separated by a pipe).
  Example: /card What powers the cell? | Mitochondria (ATP)

/daily
  Open today's daily note (created if it doesn't exist yet).

/history [n]
  List this session's commands, or re-run entry n.
  Example: /history 3

/health
  Knowledge health: your weakest notes by memory strength, and what's overdue.


━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
AI — NOTE OPERATIONS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/explain <title>
  Send the full note to AI and return a plain-English explanation of everything in it.
  Example: /explain
  Example: /explain Krebs Cycle

/summarise <title>
  Generate a concise summary of the current note or a named note.
  Alias: /summarize
  Example: /summarise

/tldr <title>
  Ultra-short 3-bullet summary. Faster and cheaper than /summarise.
  Example: /tldr Lecture 4 Notes

/expand [heading or topic]
  Ask AI to expand on a specific section or topic within the current note.
  Example: /expand oxidative phosphorylation

/improve <title>
  Ask AI to rewrite the current note for clarity, structure, and completeness.
  Example: /improve

/fix <title>
  Grammar, spelling, and phrasing corrections only — no content changes.
  Example: /fix

/tone [formal|casual|academic|bullet]
  Rewrite the current note in a different tone/style.
  Example: /tone academic

/translate [language] <title>
  Translate the current note or a named note into the target language.
  Example: /translate Spanish

/define [term]
  Ask AI to define a term and optionally insert the definition into the current note.
  Example: /define apoptosis

/eli5 <title>
  Explain the current note like the user is 12 years old.
  Example: /eli5

/critique <title>
  Ask AI to identify weaknesses, gaps, or errors in the current note's content.
  Example: /critique

/counterargument <title>
  Generate counterarguments to the claims in the current note.
  Example: /counterargument

/questions <title>
  Generate a list of exam-style questions based on the current note.
  Example: /questions
  Example: /questions Thermodynamics Notes

/keywords <title>
  Extract and list the key terms and concepts from the current note.
  Example: /keywords

/annotate <title>
  Run the full AI colour-coded annotation pass on the current note (same as Analyze button).
  Example: /annotate


━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
AI — MULTI-NOTE & VAULT OPERATIONS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/merge [NoteA] [NoteB] <output title>
  Feed both notes into AI and synthesise a new combined note. Originals untouched.
  Example: /merge "Lecture 1" "Lecture 2"
  Example: /merge "Lecture 1" "Lecture 2" "Lectures 1-2 Combined"

/compare [NoteA] [NoteB]
  Ask AI to compare and contrast two notes, highlighting agreements and contradictions.
  Example: /compare "Darwin Notes" "Lamarck Notes"

/connect [NoteA] [NoteB]
  Ask AI to find conceptual links between two notes and suggest [[wiki-link]] additions.
  Example: /connect Mitosis Meiosis

/bulk [prompt] #tag OR /bulk [prompt] all
  Apply an AI instruction to all notes matching a tag, or to the entire vault.
  Example: /bulk add a one-line summary to the frontmatter #lecture
  Example: /bulk fix spelling errors #draft

/cluster
  Ask AI to analyse the vault and suggest logical groupings or topic clusters.

/gaps
  Ask AI to analyse linked notes and identify topics that are referenced but under-documented.

/timeline <title or tag>
  Ask AI to extract and order any dates/events mentioned across a note or tag group.
  Example: /timeline #history

/debate [topic]
  Ask AI to generate a structured for/against debate on a topic, grounded in vault notes.
  Example: /debate evolution vs creationism


━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
FLASHCARDS & SPACED REPETITION
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/cards <title>
  Generate flashcards from the current note or a named note.
  Example: /cards
  Example: /cards Krebs Cycle

/cards:n [n] <title>
  Generate exactly n flashcards from the note.
  Example: /cards:n 10

/cards:hard <title>
  Generate flashcards focused specifically on the hardest or most technical concepts.
  Example: /cards:hard

/cards:fill <title>
  Generate fill-in-the-blank style cards only.
  Example: /cards:fill

/due
  Show all flashcards currently due for review across all decks.

/stats
  Show spaced repetition statistics: retention rate, cards due, streak, ease distribution.

/deck [note title]
  Jump directly to the flashcard deck for a named note.
  Example: /deck Mitosis

/forget [card id or note title]
  Reset a card or all cards from a note back to "new" status in FSRS.
  Example: /forget Mitosis


━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
GRAPH
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/graph <title>
  Open the graph tab centred on the current note or a named note.
  Example: /graph
  Example: /graph Krebs Cycle

/graph:overview [NoteA] [NoteB] ...
  Pin a set of notes in graph overview mode and generate AI summary boxes for each.
  Example: /graph:overview "Mitosis" "Meiosis" "DNA Replication"

/neighbours <title>
  List all notes directly linked to the current note or a named note.
  Example: /neighbours

/path [NoteA] [NoteB]
  Ask AI to describe the conceptual path between two notes via their shared connections.
  Example: /path "Photosynthesis" "ATP Synthesis"

/link [NoteA] [NoteB]
  Manually create a graph edge (wiki-link) between two notes.
  Example: /link Mitosis "Cell Cycle"

/unlink [NoteA] [NoteB]
  Remove a graph edge between two notes.
  Example: /unlink Mitosis "Cell Cycle"


━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
REDACTION & PRIVACY
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/redact [text or selection]
  Wrap specified text in ⟦…⟧ redaction markers in the current note.
  Example: /redact John Smith attended on 04/06/2025

/unredact
  Remove all ⟦…⟧ markers from the current note, restoring plain text.
  Example: /unredact

/redact:all
  Automatically detect and redact all PII (names, dates, locations, IDs) in the current note using AI.
  Example: /redact:all

/redact:show
  Temporarily reveal redacted content in the current session without removing markers.
  Example: /redact:show

/redact:audit
  List every redacted block across the entire vault with note names and character counts (not content).
  Example: /redact:audit

/privacy
  Show a vault-wide privacy report: notes with redactions, notes with no redactions that contain AI-detectable PII, and notes sent to AI in this session.


━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
AMBIENT & QUEUE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/surface
  Immediately trigger the ambient daemon to surface the next item (overrides interval).
  Example: /surface

/queue
  Show the current ambient speech/task queue in order.
  Example: /queue

/queue:add [note title or text]
  Add a note or freeform text to the speech queue.
  Example: /queue:add Krebs Cycle
  Example: /queue:add remember to review lecture 5 tonight

/queue:clear
  Clear the entire speech queue.
  Example: /queue:clear

/speak <title>
  Immediately read the current note or a named note aloud via TTS.
  Example: /speak
  Example: /speak Lecture 3

/pause
  Pause TTS playback.

/resume
  Resume TTS playback.

/stop
  Stop TTS playback and clear the current item.

/remind [time] [message]
  Set a one-off reminder via AlarmManager.
  Example: /remind 18:00 Review flashcards before dinner

/schedule
  Open the in-app calendar/queue manager.


━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
IMPORT & EXPORT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/import:obsidian
  Open the SAF file picker to import an Obsidian vault folder.

/import:anki
  Open the file picker to import an Anki .apkg deck.

/import:ocr
  Open the camera to scan a physical document or textbook page and create a note from it.

/import:transcript [url or file]
  Fetch a YouTube video transcript or audio file, transcribe it, and create a note.
  Example: /import:transcript https://youtube.com/watch?v=...

/export:md <title>
  Export the current note or named note as a plain .md file.
  Example: /export:md

/export:pdf <title>
  Export the current note as a PDF.
  Example: /export:pdf Revision Summary

/export:anki <title or tag>
  Export flashcards from a note or tag group as an Anki .apkg file.
  Example: /export:anki #biology

/export:vault
  Export the entire vault as a zip of markdown files (Obsidian-compatible).


━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SYSTEM & SETTINGS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/help <command>
  List all available commands, or show detailed usage for a specific command.
  Example: /help
  Example: /help merge

/help:ai
  List all AI-powered commands only.

/help:privacy
  List all redaction and privacy commands only.

/provider
  Show the currently active AI provider and model.
  Example: /provider → Gemini 1.5 Flash (BYOK)

/provider:set [provider]
  Switch the active AI provider for this session.
  Example: /provider:set groq

/provider:test
  Run a quick ping to the current AI provider to confirm the key is valid and responsive.

/key:add [provider] [key]
  Add or update a BYOK API key (stored in EncryptedSharedPreferences, never logged).
  Example: /key:add openai sk-...

/key:remove [provider]
  Remove a stored API key.
  Example: /key:remove anthropic

/key:list
  List which providers have keys stored (key values never shown).

/ambient:on
  Enable the ambient daemon.

/ambient:off
  Disable the ambient daemon.

/ambient:interval [minutes]
  Set the ambient surfacing interval.
  Example: /ambient:interval 30

/sync
  Manually trigger a Supabase vault sync.

/sync:status
  Show last sync time, pending changes count, and conflict count.

/version
  Show Bosk version, build number, and DB schema version.

/debug
  Show diagnostic info: active provider, ambient state, queue length, DB note count, last sync.

/clear
  Clear the terminal output.

/exit
  Close the AI terminal and return to the previous screen.


━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SLASH COMMAND QUICK REFERENCE (one-liner)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/new /open /delete /kill /rename /archive /duplicate /empty /undo /redo /pin /unpin /tag /untag
/tags /recent /find /orphans /broken /trash /restore /card /daily /history /health
/explain /summarise /tldr /expand /improve /fix /tone /translate /define
/eli5 /critique /counterargument /questions /keywords /annotate
/merge /compare /connect /bulk /cluster /gaps /timeline /debate
/cards /cards:n /cards:hard /cards:fill /due /stats /deck /forget
/graph /graph:overview /neighbours /path /link /unlink
/redact /unredact /redact:all /redact:show /redact:audit /privacy
/surface /queue /queue:add /queue:clear /speak /pause /resume /stop /remind /schedule
/import:obsidian /import:anki /import:ocr /import:transcript
/export:md /export:pdf /export:anki /export:vault
/help /help:ai /help:privacy /provider /provider:set /provider:test
/key:add /key:remove /key:list /ambient:on /ambient:off /ambient:interval
/sync /sync:status /version /debug /clear /exit
""".trimIndent()
}
