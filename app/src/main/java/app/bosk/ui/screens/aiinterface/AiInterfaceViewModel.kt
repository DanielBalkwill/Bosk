package app.bosk.ui.screens.aiinterface

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bosk.core.ai.llm.LlmClient
import app.bosk.core.commands.CommandNote
import app.bosk.core.commands.CommandRegistry
import app.bosk.core.commands.CommandResult
import app.bosk.core.commands.VaultActions
import app.bosk.core.notes.ActiveNoteStore
import app.bosk.core.notes.Note
import app.bosk.core.notes.NoteEditHistories
import app.bosk.core.notes.NotesRepository
import app.bosk.core.notes.Redaction
import app.bosk.core.notes.WikiLinkSyncer
import app.bosk.core.review.ReviewProgress
import app.bosk.core.review.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One printed line in the terminal history. */
data class TerminalLine(val text: String, val kind: LineKind)
enum class LineKind { SYSTEM, USER, AI }

/** A destructive command waiting on the learner's explicit go-ahead (shown as a dialog). */
data class PendingAction(
    val summary: String,
    val detail: String,
    val action: suspend () -> Unit,
)

/** A snapshot of flashcard progress for the AI-interface overview + analyzer. */
data class FlashProgress(
    val total: Int = 0,
    val due: Int = 0,
    val reviewedCount: Int = 0,
    val masteredFraction: Float = 0f,
    val recent: List<ReviewProgress> = emptyList(),
)

@HiltViewModel
class AiInterfaceViewModel @Inject constructor(
    private val activeNote: ActiveNoteStore,
    private val notes: NotesRepository,
    private val reviews: ReviewRepository,
    private val llm: LlmClient,
    private val commandRegistry: CommandRegistry,
    private val histories: NoteEditHistories,
    private val wikiLinks: WikiLinkSyncer,
    private val bulk: app.bosk.core.ai.BulkEditService,
) : ViewModel() {

    /** The most-recently used/edited notes (newest first, ≤10) — shown as ASCII frames. */
    val recentNotes: StateFlow<List<Note>> =
        combine(activeNote.recent, notes.observeNotes()) { recentIds, all ->
            val byId = all.associateBy { it.id }
            recentIds.mapNotNull { byId[it] }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _output = MutableStateFlow(
        listOf(
            TerminalLine("bosk://ai-interface — focused console", LineKind.SYSTEM),
            TerminalLine("Type a prompt, pick a suggestion, or run /help.", LineKind.SYSTEM),
        ),
    )
    val output: StateFlow<List<TerminalLine>> = _output.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** One-shot "navigate to this note in the editor" events, emitted by the `/open` command. */
    private val _openNote = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val openNote: SharedFlow<Long> = _openNote.asSharedFlow()

    /** Ghost-text continuation suggestion shown in the prompt, derived from recent notes. */
    private val _ghost = MutableStateFlow("")
    val ghost: StateFlow<String> = _ghost.asStateFlow()

    /**
     * The slash commands offered in the palette/dropdown, parsed from the user-editable command
     * note in settings. The screen filters this by what's currently typed.
     */
    val commands: StateFlow<List<CommandNote.CommandDoc>> =
        commandRegistry.commands
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Flashcard progress overview + interactive analyzer ────────────────────────────────────
    private val _progress = MutableStateFlow(FlashProgress())
    val progress: StateFlow<FlashProgress> = _progress.asStateFlow()

    private val _analyzerOpen = MutableStateFlow(false)
    val analyzerOpen: StateFlow<Boolean> = _analyzerOpen.asStateFlow()
    private val _selectedCard = MutableStateFlow<ReviewProgress?>(null)
    val selectedCard: StateFlow<ReviewProgress?> = _selectedCard.asStateFlow()
    private val _analyzerOutput = MutableStateFlow<List<TerminalLine>>(emptyList())
    val analyzerOutput: StateFlow<List<TerminalLine>> = _analyzerOutput.asStateFlow()
    private val _analyzerBusy = MutableStateFlow(false)
    val analyzerBusy: StateFlow<Boolean> = _analyzerBusy.asStateFlow()

    private fun refreshProgress() {
        viewModelScope.launch {
            val recent = runCatching { reviews.recentlyReviewed(20) }.getOrDefault(emptyList())
            val total = runCatching { reviews.totalCards() }.getOrDefault(0)
            val due = runCatching { reviews.observeDueCount().first() }.getOrDefault(0)
            // "Mastered" ≈ stability of a week or more (well-retained).
            val mastered = if (recent.isEmpty()) 0f else recent.count { it.stability >= 7.0 }.toFloat() / recent.size
            _progress.value = FlashProgress(total, due, recent.size, mastered, recent)
        }
    }

    fun openAnalyzer() { refreshProgress(); _analyzerOpen.value = true }
    fun closeAnalyzer() { _analyzerOpen.value = false }

    /** Pick a card to dig into: seed the analyzer chat with an AI take on the learner's progress on it. */
    fun selectCard(card: ReviewProgress) {
        _selectedCard.value = card
        _analyzerOutput.value = listOf(TerminalLine("analyzing: ${card.front.take(70)}", LineKind.SYSTEM))
        if (_analyzerBusy.value) return
        _analyzerBusy.value = true
        viewModelScope.launch {
            val prompt = cardContext(card) +
                "Analyze how the learner is doing on THIS card from the stats, and give concrete advice to " +
                "remember it better. Keep it to 3–4 short lines."
            llm.generate(prompt).fold(
                onSuccess = { _analyzerOutput.value = _analyzerOutput.value + TerminalLine(it.trim(), LineKind.AI) },
                onFailure = { _analyzerOutput.value = _analyzerOutput.value + TerminalLine("! ${it.message ?: "request failed"}", LineKind.SYSTEM) },
            )
            _analyzerBusy.value = false
        }
    }

    /** Converse with the AI about the currently-selected card (explain your thinking, ask for help). */
    fun runAnalyzer(rawInput: String) {
        val input = rawInput.trim()
        if (input.isEmpty() || _analyzerBusy.value) return
        _analyzerOutput.value = _analyzerOutput.value + TerminalLine("❯ $input", LineKind.USER)
        _analyzerBusy.value = true
        viewModelScope.launch {
            val prompt = (_selectedCard.value?.let { cardContext(it) } ?: "") + input
            llm.generate(prompt).fold(
                onSuccess = { _analyzerOutput.value = _analyzerOutput.value + TerminalLine(it.trim(), LineKind.AI) },
                onFailure = { _analyzerOutput.value = _analyzerOutput.value + TerminalLine("! ${it.message ?: "request failed"}", LineKind.SYSTEM) },
            )
            _analyzerBusy.value = false
        }
    }

    private fun cardContext(card: ReviewProgress): String = buildString {
        appendLine("Flashcard under discussion:")
        appendLine("Q: ${card.front}")
        appendLine("A: ${card.back}")
        appendLine(
            "Stats — reps ${card.reps}, lapses ${card.lapses}, " +
                "stability ${"%.1f".format(card.stability)} days, difficulty ${"%.1f".format(card.difficulty)}.",
        )
        appendLine()
    }

    init {
        refreshProgress()
        // Refresh the ghost suggestion whenever the set of recent notes changes.
        viewModelScope.launch {
            recentNotes.map { list -> list.map { it.id } }.distinctUntilChanged().collect { ids ->
                if (ids.isEmpty()) { _ghost.value = "open a note to get tailored suggestions"; return@collect }
                val titles = recentNotes.value.take(5).joinToString(", ") { it.title.ifBlank { "Untitled" } }
                val prompt = "In ONE short imperative line (max 12 words, no quotes), suggest what the learner " +
                    "should do next given they were just working on: $titles. e.g. 'practise insulin resistance cards' " +
                    "or 'research GLUT4 transport'."
                llm.generate(prompt).onSuccess { _ghost.value = it.trim().lineSequence().firstOrNull()?.take(80).orEmpty() }
            }
        }
    }

    private fun print(line: TerminalLine) { _output.value = _output.value + line }

    // ── Confirmation gate for destructive commands ─────────────────────────────────────────────
    private val _pendingConfirm = MutableStateFlow<PendingAction?>(null)
    val pendingConfirm: StateFlow<PendingAction?> = _pendingConfirm.asStateFlow()

    /** Park [action] behind a confirmation dialog instead of running it immediately. */
    private fun requestConfirm(summary: String, detail: String, action: suspend () -> Unit) {
        _pendingConfirm.value = PendingAction(summary, detail, action)
    }

    /** The learner tapped Run on the confirmation dialog. */
    fun confirmPending() {
        val pending = _pendingConfirm.value ?: return
        _pendingConfirm.value = null
        _busy.value = true
        viewModelScope.launch {
            try {
                pending.action()
            } catch (e: Exception) {
                print(sys("! ${e.message ?: "action failed"}"))
            } finally {
                _busy.value = false
            }
        }
    }

    /** The learner dismissed the confirmation dialog. */
    fun cancelPending() {
        if (_pendingConfirm.value == null) return
        _pendingConfirm.value = null
        print(sys("cancelled — nothing was changed"))
    }

    /**
     * Print a flashcard's contents AND its per-card progress/stats straight into the terminal as a
     * boxed block — used when the learner taps one of the flashcard chips on the main menu. It does
     * NOT open the editor or the AI analyzer; it's a quick, inline "cat" of the card.
     */
    fun printCard(card: ReviewProgress) {
        val mastered = card.stability >= 7.0
        val status = when {
            mastered -> "mastered"
            card.reps == 0 -> "new"
            card.lapses > 0 -> "relearning"
            else -> "learning"
        }
        val now = System.currentTimeMillis()
        val dueIn = card.dueMs - now
        val dueText = when {
            dueIn <= 0 -> "due now"
            else -> "due in ${(dueIn / 86_400_000.0).let { if (it >= 1) "%.0fd".format(it) else "%.0fh".format(dueIn / 3_600_000.0) }}"
        }
        val rule = "─".repeat(40)
        val block = listOf(
            TerminalLine("┌─ flashcard $rule", LineKind.SYSTEM),
            TerminalLine("Q: ${card.front.ifBlank { "(blank)" }}", LineKind.AI),
            TerminalLine("A: ${card.back.ifBlank { "(blank)" }}", LineKind.AI),
            TerminalLine("── stats ──", LineKind.SYSTEM),
            TerminalLine(
                "reps ${card.reps} · lapses ${card.lapses} · " +
                    "stability ${"%.1f".format(card.stability)}d · difficulty ${"%.1f".format(card.difficulty)}",
                LineKind.SYSTEM,
            ),
            TerminalLine("status: $status · $dueText", LineKind.SYSTEM),
            TerminalLine("└$rule────────────", LineKind.SYSTEM),
        )
        _output.value = _output.value + block
    }

    /** This session's submitted lines, oldest first — powers /history and the ↑ recall button. */
    private val _history = MutableStateFlow<List<String>>(emptyList())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    /** All note titles — drives tap-to-complete suggestions for command arguments. */
    val noteTitles: StateFlow<List<String>> =
        notes.observeNotes()
            .map { list -> list.map { it.title }.filter { it.isNotBlank() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Replace the trailing AI line's text — the streaming sink for live terminal output. */
    private fun updateLastAiLine(text: String) {
        val list = _output.value
        if (list.isEmpty() || list.last().kind != LineKind.AI) return
        _output.value = list.dropLast(1) + TerminalLine(text, LineKind.AI)
    }

    fun run(rawInput: String) {
        val input = rawInput.trim()
        if (input.isEmpty() || _busy.value) return
        print(TerminalLine("❯ $input", LineKind.USER))
        if (!input.startsWith("/history")) _history.value = _history.value + input

        _busy.value = true
        viewModelScope.launch {
            // First give the real-action dispatcher a chance: a handful of slash commands perform
            // concrete, measurable work on the vault (create/delete notes, search, list) rather than
            // just talking to the AI. If it handles the line, we're done.
            if (tryRunAction(input)) { _busy.value = false; return@launch }
            // Otherwise route through the shared command engine: /help & /clear are answered locally;
            // a slash command is expanded into an instruction; plain text passes through unchanged.
            when (val result = commandRegistry.process(input)) {
                is CommandResult.Clear -> _output.value = emptyList()
                is CommandResult.Help ->
                    result.text.lineSequence().forEach { print(TerminalLine(it, LineKind.SYSTEM)) }
                is CommandResult.Prompt -> {
                    // STREAM the reply into the last line so long answers appear live.
                    val prompt = buildPrompt(result.text)
                    print(TerminalLine("", LineKind.AI))
                    val buf = StringBuilder()
                    try {
                        llm.generateStream(prompt).collect { chunk ->
                            buf.append(chunk)
                            updateLastAiLine(buf.toString().trim())
                        }
                        if (buf.isBlank()) updateLastAiLine("(no reply)")
                    } catch (e: Exception) {
                        _output.value = _output.value.dropLast(1)
                        print(TerminalLine("! ${e.message ?: "request failed"}", LineKind.SYSTEM))
                    }
                }
            }
            _busy.value = false
        }
    }

    private fun sys(text: String) = TerminalLine(text, LineKind.SYSTEM)

    /** Resolve a command's note argument: blank → the active note; otherwise a case-insensitive title match. */
    private suspend fun resolveNote(arg: String): Note? =
        if (arg.isBlank()) activeNote.id.value?.let { notes.get(it) }
        else notes.observeNotes().first().firstOrNull { it.title.equals(arg, ignoreCase = true) }
            ?: notes.observeNotes().first().firstOrNull { it.title.contains(arg, ignoreCase = true) }

    /**
     * A small, deliberately-basic set of slash commands that perform REAL, measurable actions on the
     * vault (not just AI prompts). Returns true if the line was a recognised action. Easy to extend:
     * add another `"/name"` branch. Anything not matched here falls through to the AI command engine.
     */
    private suspend fun tryRunAction(input: String): Boolean {
        val text = input.trim()
        if (!text.startsWith("/")) {
            // Natural-language "empty the note" acts for real, on the active note. Bare "clear"
            // is excluded — in a terminal that means "clear the output" (the /clear route).
            if (VaultActions.isEmptyIntent(text) && !text.equals("clear", ignoreCase = true)) {
                confirmEmpty(resolveNote(""))
                return true
            }
            return false
        }
        val name = text.substringBefore(' ').lowercase()
        val arg = text.removePrefix(text.substringBefore(' ')).trim()
        when (name) {
            // Delete a note (soft-delete → recycle bin). Active note if no title given.
            // Destructive → parked behind the confirmation dialog.
            "/kill", "/delete" -> {
                val target = resolveNote(arg)
                if (target == null) {
                    print(sys("! no note to delete — open one first or pass a title, e.g. /kill Mitosis"))
                } else {
                    val title = target.title.ifBlank { "Untitled" }
                    requestConfirm(
                        summary = "Delete \"$title\"?",
                        detail = "The note moves to the recycle bin (bring it back with /restore).",
                    ) {
                        notes.delete(target.id)
                        print(sys("✓ moved \"$title\" to the recycle bin"))
                    }
                }
            }
            // Empty a note's body (active note if no title given). Destructive → confirmed first.
            "/empty", "/wipe" -> confirmEmpty(resolveNote(arg))
            // Undo / redo a note's last body change via the shared edit-history registry —
            // reverses terminal /empty, /bulk edits, and editor changes alike.
            "/undo", "/redo" -> {
                val target = resolveNote(arg)
                if (target == null) { print(sys("! no note — open one first or pass a title")); return true }
                val title = target.title.ifBlank { "Untitled" }
                val h = histories.forNote(target.id)
                val newBody = if (name == "/undo") h.undo() else h.redo()
                if (newBody == null) {
                    print(sys("nothing to ${name.removePrefix("/")} for \"$title\" this session"))
                } else {
                    notes.upsert(target.copy(body = newBody))
                    wikiLinks.sync(target.copy(body = newBody))
                    print(sys("✓ ${if (name == "/undo") "undid" else "redid"} last change to \"$title\" (${newBody.length} chars now)"))
                }
            }
            // Apply one instruction to MANY notes (shared engine with the Notes bar + editor).
            // Touches up to 60 notes in one go → parked behind the confirmation dialog.
            "/bulk" -> {
                if (arg.isBlank()) { print(sys("usage: /bulk <instruction> — e.g. /bulk fix spelling #biology")); return true }
                requestConfirm(
                    summary = "Run bulk edit?",
                    detail = "\"$arg\" — this can modify many notes at once. Each note's change is undoable (↶) in its editor.",
                ) {
                    val result = runCatching { bulk.run(arg) { p -> print(sys(p)) } }
                        .getOrElse { "! bulk edit failed: ${it.message ?: "unknown error"}" }
                    print(sys(result))
                }
            }
            // Create a new (empty-bodied) note and focus it.
            "/new" -> {
                val title = arg.ifBlank { "Untitled" }
                val id = notes.upsert(Note(title = title))
                activeNote.set(id)
                print(sys("✓ created note #$id \"$title\""))
            }
            // Full-text search; print the top matches.
            "/find" -> {
                if (arg.isBlank()) { print(sys("usage: /find <query>")); return true }
                val results = notes.search(arg)
                if (results.isEmpty()) print(sys("no matches for \"$arg\""))
                else {
                    print(sys("${results.size} match${if (results.size == 1) "" else "es"}:"))
                    results.take(10).forEach { print(TerminalLine("  • ${it.title.ifBlank { "Untitled" }}", LineKind.AI)) }
                }
            }
            // List the most-recently focused notes.
            "/recent" -> {
                val n = arg.toIntOrNull()?.coerceIn(1, 30) ?: 10
                val list = recentNotes.value.take(n)
                if (list.isEmpty()) print(sys("no recent notes yet"))
                else { print(sys("recent ${list.size}:")); list.forEach { print(TerminalLine("  • ${it.title.ifBlank { "Untitled" }}", LineKind.AI)) } }
            }
            // Show flashcards currently due for review.
            "/due" -> {
                val due = runCatching { reviews.dueCards(30) }.getOrDefault(emptyList())
                print(sys("${due.size} card${if (due.size == 1) "" else "s"} due"))
                due.take(10).forEach { print(TerminalLine("  • ${it.front.take(60)}", LineKind.AI)) }
            }
            // Open a note in the editor (focus it + emit a navigation event the screen handles).
            "/open" -> {
                if (arg.isBlank()) { print(sys("usage: /open <title>")); return true }
                val target = resolveNote(arg)
                if (target == null) print(sys("no note matching \"$arg\""))
                else {
                    activeNote.set(target.id)
                    _openNote.tryEmit(target.id)
                    print(sys("✓ opening \"${target.title.ifBlank { "Untitled" }}\""))
                }
            }
            // Rename the active note (no title argument operates on the focused note).
            "/rename" -> {
                if (arg.isBlank()) { print(sys("usage: /rename <new title>")); return true }
                val target = resolveNote("")
                if (target == null) print(sys("! no active note — open one first"))
                else {
                    notes.upsert(target.copy(title = arg))
                    print(sys("✓ renamed to \"$arg\""))
                }
            }
            // Duplicate a note (active if unnamed) with a "(copy)" suffix and focus the copy.
            "/duplicate", "/dup" -> {
                val target = resolveNote(arg)
                if (target == null) print(sys("! no note to duplicate — open one or pass a title"))
                else {
                    val id = notes.upsert(
                        target.copy(id = 0, title = VaultActions.copyTitle(target.title)),
                    )
                    activeNote.set(id)
                    print(sys("✓ duplicated as \"${VaultActions.copyTitle(target.title)}\" (#$id)"))
                }
            }
            // Add a tag: /tag <tag> [title] (title blank → active note).
            "/tag" -> {
                val parsed = VaultActions.parseTagArg(arg)
                if (parsed == null) { print(sys("usage: /tag <tag> [title]")); return true }
                val (tag, title) = parsed
                val target = resolveNote(title)
                if (target == null) print(sys("! no note to tag — open one or pass a title"))
                else {
                    notes.upsert(target.copy(tags = VaultActions.addTag(target.tags, tag)))
                    print(sys("✓ tagged \"${target.title.ifBlank { "Untitled" }}\" #$tag"))
                }
            }
            // Remove a tag: /untag <tag> [title].
            "/untag" -> {
                val parsed = VaultActions.parseTagArg(arg)
                if (parsed == null) { print(sys("usage: /untag <tag> [title]")); return true }
                val (tag, title) = parsed
                val target = resolveNote(title)
                if (target == null) print(sys("! no note — open one or pass a title"))
                else {
                    notes.upsert(target.copy(tags = VaultActions.removeTag(target.tags, tag)))
                    print(sys("✓ removed #$tag from \"${target.title.ifBlank { "Untitled" }}\""))
                }
            }
            // List every tag in the vault with its note count.
            "/tags" -> {
                val counts = VaultActions.tagCounts(notes.observeNotes().first())
                if (counts.isEmpty()) print(sys("no tags yet"))
                else {
                    print(sys("${counts.size} tag${if (counts.size == 1) "" else "s"}:"))
                    counts.forEach { (tag, n) -> print(TerminalLine("  • #$tag ($n)", LineKind.AI)) }
                }
            }
            // Spaced-repetition stats: totals, due, and mastery over the recent sample.
            "/stats" -> {
                val total = runCatching { reviews.totalCards() }.getOrDefault(0)
                val due = runCatching { reviews.observeDueCount().first() }.getOrDefault(0)
                val recent = runCatching { reviews.recentlyReviewed(20) }.getOrDefault(emptyList())
                val mastered = if (recent.isEmpty()) 0f
                else recent.count { it.stability >= 7.0 }.toFloat() / recent.size
                VaultActions.statsLines(total, due, recent.size, mastered).forEach { print(sys(it)) }
            }
            // List the recycle bin.
            "/trash", "/bin" -> {
                val trashed = notes.observeTrashed().first()
                if (trashed.isEmpty()) print(sys("recycle bin is empty"))
                else {
                    print(sys("${trashed.size} in bin:"))
                    trashed.take(20).forEach { print(TerminalLine("  • ${it.title.ifBlank { "Untitled" }}", LineKind.AI)) }
                }
            }
            // Create a flashcard directly: /card <front> | <back> (attached to the active note).
            "/card" -> {
                val parts = arg.split("|", limit = 2).map { it.trim() }
                if (parts.size < 2 || parts.any { it.isBlank() }) {
                    print(sys("usage: /card <front> | <back>"))
                    return true
                }
                val target = resolveNote("")
                if (target == null) print(sys("! no active note to attach the card to — open one first"))
                else {
                    reviews.addCard(target.id, parts[0], parts[1], aiGenerated = false)
                    print(sys("✓ card added to \"${target.title.ifBlank { "Untitled" }}\" — Q: ${parts[0].take(50)}"))
                }
            }
            // Open (creating if needed) today's daily note.
            "/daily" -> {
                val title = "Daily " + java.time.LocalDate.now().toString()
                val existing = notes.observeNotes().first().firstOrNull { it.title.equals(title, ignoreCase = true) }
                val id = existing?.id ?: notes.upsert(Note(title = title, tags = listOf("daily")))
                activeNote.set(id)
                _openNote.tryEmit(id)
                print(sys(if (existing != null) "✓ opening $title" else "✓ created + opening $title"))
            }
            // List this session's commands, or re-run one by number.
            "/history" -> {
                val hist = _history.value
                val n = arg.toIntOrNull()
                when {
                    hist.isEmpty() -> print(sys("no history yet this session"))
                    n != null -> {
                        val entry = hist.getOrNull(n - 1)
                        if (entry == null) print(sys("no history entry $n"))
                        else { print(sys("↻ re-running: $entry")); _busy.value = false; run(entry); return true }
                    }
                    else -> {
                        print(sys("${hist.size} command${if (hist.size == 1) "" else "s"} this session:"))
                        hist.takeLast(15).forEachIndexed { i, cmd ->
                            val idx = hist.size - minOf(15, hist.size) + i + 1
                            print(TerminalLine("  $idx. $cmd", LineKind.AI))
                        }
                    }
                }
            }
            // Knowledge health: the weakest notes by memory strength + what's overdue.
            "/health" -> {
                val health = runCatching { reviews.observeNoteHealth().first() }.getOrDefault(emptyMap())
                if (health.isEmpty()) { print(sys("no flashcards yet — nothing to measure")); return true }
                val titles = notes.observeNotes().first().associate { it.id to it.title }
                val ranked = health.entries.sortedBy { it.value.score }
                val overdue = health.values.sumOf { it.due }
                print(sys("knowledge health — ${health.size} deck${if (health.size == 1) "" else "s"}, $overdue card${if (overdue == 1) "" else "s"} overdue"))
                print(sys("weakest first:"))
                ranked.take(8).forEach { (noteId, h) ->
                    val bar = "█".repeat((h.score * 10).toInt().coerceIn(0, 10)).padEnd(10, '░')
                    print(TerminalLine("  $bar ${(h.score * 100).toInt()}%  ${titles[noteId] ?: "Note $noteId"} (${h.due} due)", LineKind.AI))
                }
            }
            // Restore a trashed note by title.
            "/restore" -> {
                if (arg.isBlank()) { print(sys("usage: /restore <title>")); return true }
                val target = notes.observeTrashed().first().firstOrNull {
                    it.title.equals(arg, ignoreCase = true)
                } ?: notes.observeTrashed().first().firstOrNull {
                    it.title.contains(arg, ignoreCase = true)
                }
                if (target == null) print(sys("no trashed note matching \"$arg\""))
                else {
                    notes.restore(target.id)
                    print(sys("✓ restored \"${target.title.ifBlank { "Untitled" }}\""))
                }
            }
            else -> return false
        }
        return true
    }

    /**
     * Gate emptying a note behind the confirmation dialog. The no-target / already-empty cases
     * print immediately (nothing destructive to confirm).
     */
    private fun confirmEmpty(target: Note?) {
        if (target == null) {
            print(sys("! no note to empty — open one first or pass a title, e.g. /empty Mitosis"))
            return
        }
        val title = target.title.ifBlank { "Untitled" }
        if (target.body.isBlank()) {
            print(sys("\"$title\" is already empty"))
            return
        }
        requestConfirm(
            summary = "Empty \"$title\"?",
            detail = "Removes ${target.body.length} characters. Undo (↶) in the editor brings them back.",
        ) { emptyNote(target) }
    }

    /**
     * Empty a note's body FOR REAL. The change is bracketed into the note's shared edit history
     * ([NoteEditHistories]) so the editor toolbar's undo (↶) brings the body straight back, and
     * the graph edges derived from the old body are re-synced away.
     */
    private suspend fun emptyNote(target: Note) {
        val title = target.title.ifBlank { "Untitled" }
        histories.recordExternalEdit(target.id, target.body, "")
        notes.upsert(target.copy(body = ""))
        wikiLinks.sync(target.copy(body = "")) // the emptied body's [[link]] edges leave the graph
        print(sys("✓ emptied \"$title\" (${target.body.length} chars removed) — undo (↶) in the editor brings it back"))
    }

    /** Ground the (already command-expanded) instruction in the learner's recent notes. */
    private fun buildPrompt(instruction: String): String {
        val recents = recentNotes.value.take(6)
        val context = if (recents.isEmpty()) "" else buildString {
            appendLine("The learner's recent notes:")
            recents.forEach { n ->
                appendLine("- ${n.title.ifBlank { "Untitled" }}: ${Redaction.forAi(n.body).take(200)}")
            }
            appendLine()
        }
        return context + instruction
    }
}
