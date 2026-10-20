package com.generic.mnemos.ui.screens.notes

import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.generic.mnemos.core.ai.NoteAiService
import com.generic.mnemos.core.ai.NoteAnnotation
import com.generic.mnemos.core.ai.RecentCommandsStore
import com.generic.mnemos.core.commands.CommandNote
import com.generic.mnemos.core.commands.CommandRegistry
import com.generic.mnemos.core.commands.CommandResult
import com.generic.mnemos.core.graph.GraphRepository
import com.generic.mnemos.core.commands.VaultActions
import com.generic.mnemos.core.notes.ActiveNoteStore
import com.generic.mnemos.core.notes.EditHistory
import com.generic.mnemos.core.notes.ImageStore
import com.generic.mnemos.core.notes.NoteEditHistories
import com.generic.mnemos.core.notes.Note
import com.generic.mnemos.core.notes.NotesRepository
import com.generic.mnemos.core.notes.Redaction
import com.generic.mnemos.core.notes.WikiLinkSyncer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** AI panel state for the editor. */
sealed interface AiState {
    data object Idle : AiState
    data class Loading(val label: String) : AiState
    data class Result(val title: String, val body: String) : AiState
    data class Error(val message: String) : AiState
}

/** The note-analysis overlay (colour-coded annotations; client-only until saved). */
sealed interface AnalysisState {
    data object Idle : AnalysisState
    data object Loading : AnalysisState
    data class Ready(val annotations: List<NoteAnnotation>) : AnalysisState
    data class Error(val message: String) : AnalysisState
}

data class EditorState(
    val id: Long = 0,
    val title: String = "",
    val body: String = "",
    val tagsText: String = "",
    val saving: Boolean = false,
    val ai: AiState = AiState.Idle,
    /** True while the AI is streaming a rewrite of the body into the editor. */
    val aiComposing: Boolean = false,
    /** AI reply that should be shown in a box (a question answered, NOT written to the note). */
    val aiAnswer: String? = null,
    val analysis: AnalysisState = AnalysisState.Idle,
    /** Undo/redo availability for the toolbar buttons (driven by [EditHistory]). */
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** True once Connect has run this session — reveals the "View connections graph" button. */
    val connectionsSuggested: Boolean = false,
)

/** How [NoteEditorViewModel.aiAssist] is routing the current stream. */
private enum class AssistMode { UNKNOWN, EDIT, ANSWER }

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val notes: NotesRepository,
    private val graph: GraphRepository,
    private val wikiLinks: WikiLinkSyncer,
    private val ai: NoteAiService,
    private val imageStore: ImageStore,
    private val activeNote: ActiveNoteStore,
    private val settings: com.generic.mnemos.core.settings.SettingsRepository,
    private val commandRegistry: CommandRegistry,
    private val recentCommands: RecentCommandsStore,
    private val reviews: com.generic.mnemos.core.review.ReviewRepository,
    private val histories: NoteEditHistories,
    private val bulk: com.generic.mnemos.core.ai.BulkEditService,
) : ViewModel() {

    /** Recently-sent AI instructions, surfaced as quick re-run buttons in the AI dropdown. */
    val recentAiCommands: StateFlow<List<String>> = recentCommands.commands

    private val initialId: Long = savedStateHandle.get<Long>("noteId") ?: -1L

    /** Slash commands for the editor's AI palette (driven by the editable command note). */
    val commands: StateFlow<List<CommandNote.CommandDoc>> =
        commandRegistry.commands.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Accessibility font size (sp), shared with Settings and persisted; updates the editor live. */
    val fontSp: StateFlow<Float> =
        settings.noteFontSp.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 16f)
    fun setFontSp(sp: Float) { viewModelScope.launch { settings.setNoteFontSp(sp) } }

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    /**
     * Undo/redo history of the note body — the SHARED per-note stack from [NoteEditHistories],
     * so edits made outside the editor (e.g. the terminal's `/empty`) are undoable here too.
     * A new note starts on a private stack that is adopted into the registry once it gets an id.
     */
    private var history = EditHistory()

    /**
     * Reactive "how many connections does this note have" — for the indicator + graph button.
     * DEFERRED: the graph subscription opens ~600ms after the editor does, so opening a note
     * never pays for the graph up front (the count is cosmetic; the graph itself only loads
     * when its screen is opened).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val connectionCount: StateFlow<Int> =
        _state.map { it.id }.distinctUntilChanged()
            .flatMapLatest { id ->
                if (id > 0) graph.observeConnectionCount(id).onStart { delay(600) } else flowOf(0)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * ONE Room subscription feeds both title-derived flows below (it used to be two, each
     * remapping the whole vault on every note write). StateFlow's equality skip means neither
     * downstream emits while typing only changes bodies.
     */
    private val titlePairs: StateFlow<List<Pair<Long, String>>> =
        notes.observeNotes()
            .map { list -> list.mapNotNull { n -> n.title.takeIf { it.isNotBlank() }?.let { t -> n.id to t } } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Known note titles — drives `[[ ]]` / `!link` highlighting + the `!` suggestion menu. */
    val noteTitles: StateFlow<List<String>> =
        titlePairs.map { pairs -> pairs.map { it.second } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Title (lowercased) → note id, so a tapped `[[link]]`/`!link` can be opened. */
    val noteIdsByTitle: StateFlow<Map<String, Long>> =
        titlePairs.map { pairs -> pairs.associate { it.second.trim().lowercase() to it.first } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private var saveJob: Job? = null

    init {
        if (initialId > 0) {
            // Focus this note app-wide, so swiping back to Home shows it in "Now Reading".
            activeNote.set(initialId)
            history = histories.forNote(initialId)
            viewModelScope.launch {
                try {
                    notes.get(initialId)?.let { n ->
                        _state.update {
                            it.copy(
                                id = n.id,
                                title = n.title,
                                body = n.body,
                                tagsText = n.tags.joinToString(" ") { t -> "#$t" },
                            )
                        }
                        // First open this session seeds the stack; a returning open keeps the
                        // existing trail (commit is a no-op when the loaded body is already the
                        // top, and records one extra step when it changed outside the editor).
                        if (history.current == null) history.reset(n.body) else history.commit(n.body)
                        refreshHistoryFlags()
                    }
                } catch (e: Exception) {
                    Log.e("NoteEditor", "load failed", e) // leave a blank editor rather than crash
                }
            }
        } else {
            history.reset("") // new note: empty baseline so the first edits are undoable
        }
    }

    // ── Undo / redo ──────────────────────────────────────────────────────────────────────────

    /** Snapshot the current body into the history (no-op if unchanged). Called at settle points. */
    fun recordHistory() {
        history.commit(_state.value.body)
        refreshHistoryFlags()
    }

    private fun refreshHistoryFlags() {
        _state.update { it.copy(canUndo = history.canUndo, canRedo = history.canRedo) }
    }

    /** Saved body snapshots of this note, newest first — the History dialog's data. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val versions: StateFlow<List<com.generic.mnemos.core.notes.NoteVersion>> =
        _state.map { it.id }.distinctUntilChanged()
            .flatMapLatest { id -> if (id > 0) notes.observeVersions(id) else flowOf(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Restore a previous version's body — one clean step, reversible with undo (↶). */
    fun restoreVersion(version: com.generic.mnemos.core.notes.NoteVersion) {
        recordHistory() // snapshot the current body first, so ↶ returns to it
        saveJob?.cancel()
        _state.update { it.copy(body = version.body) }
        recordHistory()
        save()
    }

    fun undo() {
        val text = history.undo() ?: return
        applyHistoryBody(text)
    }

    fun redo() {
        val text = history.redo() ?: return
        applyHistoryBody(text)
    }

    /** Apply a body produced by undo/redo: set it, refresh flags, persist — without re-recording. */
    private fun applyHistoryBody(text: String) {
        saveJob?.cancel() // don't let a pending debounce re-commit and clobber the redo tail
        _state.update { it.copy(body = text) }
        refreshHistoryFlags()
        save()
    }

    // ── Redaction (toolbar Redact mode: tap a word, or drag a span) ───────────────────────────

    /** Toggle redaction on the whole word at [offset] (tap-to-redact). Bracketed for undo. */
    fun redactWordAt(offset: Int) {
        recordHistory() // capture the pre-redaction state
        val r = Redaction.redactWordAt(_state.value.body, offset)
        _state.update { it.copy(body = r.text) }
        recordHistory() // and the post-redaction state, as its own undo step
        scheduleSave()
    }

    /** Toggle redaction on the selected [start, end) span (drag-to-redact). Bracketed for undo. */
    fun redactRange(start: Int, end: Int) {
        recordHistory()
        val r = Redaction.toggle(_state.value.body, start, end)
        _state.update { it.copy(body = r.text) }
        recordHistory()
        scheduleSave()
    }

    /** Copy a picked image into the store and append an Obsidian-style `![[name]]` embed. */
    fun attachImage(uri: Uri) {
        viewModelScope.launch {
            val name = imageStore.importUri(uri, null) ?: return@launch
            _state.update {
                val sep = if (it.body.isEmpty() || it.body.endsWith("\n")) "" else "\n"
                it.copy(body = it.body + "$sep![[$name]]\n")
            }
            scheduleSave()
        }
    }

    /** Resolve an embedded image name to its on-disk file (for display). */
    fun imageFile(name: String): File = imageStore.fileFor(name)

    fun isImageName(name: String): Boolean = imageStore.isImageName(name)

    fun onTitleChange(v: String) { _state.update { it.copy(title = v) }; scheduleSave() }
    fun onBodyChange(v: String) { _state.update { it.copy(body = v) }; scheduleSave() }
    fun onTagsChange(v: String) { _state.update { it.copy(tagsText = v) }; scheduleSave() }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(700) // debounce
            recordHistory() // snapshot the settled body so each typing pause is one undo step
            save()
        }
    }

    /** Persist immediately (also called on screen dispose). */
    fun save() {
        val s = _state.value
        if (s.title.isBlank() && s.body.isBlank()) return // nothing worth saving yet
        viewModelScope.launch {
            // Guard: an uncaught exception in a launch{} crashes the whole app. A note save
            // must never take the app down (this was crashing on exit).
            try {
                _state.update { it.copy(saving = true) }
                val note = Note(
                    id = s.id,
                    title = s.title,
                    body = s.body,
                    tags = mergeTags(s.tagsText, s.body),
                )
                val id = notes.upsert(note)
                // A new note's undo trail joins the shared registry once its real id exists.
                if (s.id <= 0) histories.adopt(id, history)
                // Re-derive [[wiki-link]] edges from the body (also ensures this note's node).
                wikiLinks.sync(note.copy(id = id))
                activeNote.set(id) // keep Home's preview pointed at the note being edited
                _state.update { it.copy(id = id, saving = false) }
            } catch (e: Exception) {
                Log.e("NoteEditor", "save failed", e)
                _state.update { it.copy(saving = false) }
            }
        }
    }

    /** The note as shareable plain text: title + body, redaction markers stripped (text kept). */
    fun shareableText(): String {
        val s = _state.value
        val body = Redaction.stripMarkers(s.body)
        return if (s.title.isBlank()) body else "${s.title}\n\n$body"
    }

    /** Duplicate this note as "<title> (copy)" and hand the new id to the caller to open. */
    fun duplicateNote(onReady: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                ensureSaved()
                val s = _state.value
                val copy = Note(
                    title = "${s.title.ifBlank { "Untitled" }} (copy)",
                    body = s.body,
                    tags = mergeTags(s.tagsText, s.body),
                )
                val id = notes.upsert(copy)
                wikiLinks.sync(copy.copy(id = id)) // the copy gets its own node + link edges
                onReady(id)
            } catch (e: Exception) {
                Log.e("NoteEditor", "duplicate failed", e)
            }
        }
    }

    /** Move this note to the trash (restorable from the notes list). Unsaved notes just close. */
    fun deleteNote(onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                saveJob?.cancel() // don't let a pending autosave resurrect it
                val id = _state.value.id
                if (id > 0) notes.delete(id)
                onDone()
            } catch (e: Exception) {
                Log.e("NoteEditor", "delete failed", e)
            }
        }
    }

    fun summarise() = aiAction("Summarising") {
        ai.summarise(currentNote()).map { AiState.Result("Summary", it) }
    }

    fun explain() = aiAction("Explaining") {
        val concept = _state.value.title.ifBlank { "this note" }
        ai.explain(concept).map { AiState.Result("Explanation", it) }
    }

    /** Explain just the area-selected excerpt (the editor's drag-to-select Explain). */
    fun explainSelection(excerpt: String) {
        val text = excerpt.trim()
        if (text.isBlank()) return
        aiAction("Explaining") {
            ai.explain(text).map { AiState.Result("Explanation", it) }
        }
    }

    fun generateCards() = aiAction("Generating cards") {
        ensureSaved()
        ai.generateCards(currentNote()).map {
            AiState.Result("Flashcards", "Created $it card${if (it == 1) "" else "s"} from this note.")
        }
    }

    fun findConnections() = aiAction("Finding connections") {
        ensureSaved()
        ai.findConnections(currentNote()).map {
            _state.update { s -> s.copy(connectionsSuggested = true) }
            AiState.Result(
                "Connections",
                if (it == 0) "No strong connections found yet — tap View connections graph to see the existing ones."
                else "Suggested $it connection${if (it == 1) "" else "s"} — tap View connections graph below (suggestions are gold).",
            )
        }
    }

    fun dismissAi() { _state.update { it.copy(ai = AiState.Idle) } }

    /**
     * Run a free-text [instruction] against the open note. The model decides whether it's an EDIT
     * (the rewrite streams straight into the body, live) or an ANSWER (a question/aside that is
     * shown in a response box and never touches the note). Edits persist + re-sync links on finish.
     */
    fun aiAssist(instruction: String) {
        val q = instruction.trim()
        if (q.isBlank() || _state.value.aiComposing) return
        recentCommands.record(q) // remember it as a quick re-run button
        // A few obvious note operations run LOCALLY — instant, offline, and fully undoable via
        // the toolbar's back/forward (the AI stream can't even express "empty body").
        if (tryLocalAction(q)) return
        // Multi-note edits route to the shared bulk engine (same behaviour as the Notes tab bar
        // and the terminal); progress + result land in the answer box, never in this note.
        if (q.startsWith("/bulk", ignoreCase = true)) {
            runBulk(q.substring("/bulk".length).trim())
            return
        }
        viewModelScope.launch {
            // Route slash commands through the shared engine: /help shows in the answer box, a
            // slash command becomes a concrete instruction, plain text passes through.
            when (val result = commandRegistry.process(q)) {
                is CommandResult.Help -> _state.update { it.copy(aiAnswer = result.text) }
                is CommandResult.Clear -> _state.update { it.copy(aiAnswer = null) }
                is CommandResult.Prompt -> streamAssist(result.text)
            }
        }
    }

    /** Drop a remembered instruction chip (the ✕ on it). */
    fun removeRecentCommand(command: String) = recentCommands.remove(command)

    /** `/bulk <instruction>` from the AI dropdown: edit many notes via the shared bulk engine. */
    private fun runBulk(request: String) {
        if (_state.value.aiComposing) return
        if (request.isBlank()) {
            _state.update { it.copy(aiAnswer = "Usage: /bulk <instruction> — e.g. /bulk fix spelling #biology") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(aiComposing = true) }
            try {
                val result = bulk.run(request) { p -> _state.update { it.copy(aiAnswer = p) } }
                _state.update { it.copy(aiAnswer = result) }
                // If the OPEN note was among those edited, pull the new body in (and keep the
                // debounced save from clobbering it with the stale editor text).
                val id = _state.value.id
                if (id > 0) notes.get(id)?.let { n ->
                    if (n.body != _state.value.body) {
                        saveJob?.cancel()
                        _state.update { it.copy(body = n.body) }
                        refreshHistoryFlags() // bulk already recorded the undo step
                    }
                }
            } catch (e: Exception) {
                Log.e("NoteEditor", "bulk edit failed", e)
                _state.update { it.copy(aiAnswer = "Bulk edit failed: ${e.message ?: "unknown error"}") }
            } finally {
                _state.update { it.copy(aiComposing = false) }
            }
        }
    }

    /**
     * Concrete editor operations handled without the AI. Returns true when [q] was handled.
     * Every action is bracketed with history commits so undo/redo steps through it cleanly.
     * The "empty the note" intent matcher lives in [VaultActions] — shared with the terminal.
     */
    private fun tryLocalAction(q: String): Boolean {
        if (VaultActions.isEmptyIntent(q)) {
            recordHistory() // pre-action snapshot
            saveJob?.cancel()
            _state.update { it.copy(body = "", aiAnswer = "Note emptied — Undo (↶) brings it back.") }
            recordHistory() // post-action snapshot: one clean undo step
            save()
            return true
        }
        return false
    }

    /** Stream an AI edit/answer for [instruction] against the open note (EDIT vs ANSWER auto-routed). */
    private suspend fun streamAssist(instruction: String) {
        saveJob?.cancel() // don't let the debounced save race the live stream
        val note = currentNote()
        val originalBody = note.body
        recordHistory() // snapshot pre-rewrite so the whole AI edit is one undo step
        _state.update { it.copy(aiComposing = true, aiAnswer = null) }
        run {
            val buf = StringBuilder()
            var mode = AssistMode.UNKNOWN
            val marker = NoteAiService.ANSWER_MARKER
            try {
                ai.assistStream(note, instruction).collect { chunk ->
                    buf.append(chunk)
                    val trimmed = buf.toString().trimStart()
                    if (mode == AssistMode.UNKNOWN) {
                        // Wait until we've seen enough to tell an "@answer " reply from a note body.
                        mode = when {
                            trimmed.startsWith(marker) -> AssistMode.ANSWER
                            marker.startsWith(trimmed) -> AssistMode.UNKNOWN // still ambiguous; keep buffering
                            else -> AssistMode.EDIT
                        }
                    }
                    when (mode) {
                        AssistMode.EDIT -> _state.update { it.copy(body = buf.toString()) }
                        AssistMode.ANSWER ->
                            _state.update { it.copy(aiAnswer = trimmed.removePrefix(marker).trimStart()) }
                        AssistMode.UNKNOWN -> Unit
                    }
                }
            } catch (e: Exception) {
                Log.e("NoteEditor", "ai assist failed", e)
                _state.update { it.copy(ai = AiState.Error(e.message ?: "AI request failed.")) }
            } finally {
                _state.update { it.copy(aiComposing = false) }
                when (mode) {
                    AssistMode.EDIT -> { recordHistory(); save() } // snapshot the rewrite; persist + re-derive links
                    // Answer/ambiguous: make sure we never left a half-written body behind.
                    else -> if (_state.value.body != originalBody) _state.update { it.copy(body = originalBody) }
                }
            }
        }
    }

    fun dismissAnswer() = _state.update { it.copy(aiAnswer = null) }

    // ── Selection AI actions (shown when text is selected in the body) ─────────────────────────

    /** Ask the AI about the selected excerpt; the reply lands in the answer box (never the note). */
    fun askAboutSelection(excerpt: String) {
        val text = excerpt.trim()
        if (text.isBlank()) return
        // Phrase it as a question so assistStream routes it to an answer, not a body rewrite.
        aiAssist("What does this mean / explain this excerpt: \"$text\"")
    }

    /**
     * Redaction → cloze cards: every ⟦redacted⟧ span becomes a flashcard (front = the sentence
     * with the span blanked, back = the hidden text). No AI call — instant and free.
     */
    fun clozeFromRedactions() {
        val clozes = com.generic.mnemos.core.review.ClozeCards.fromRedactions(_state.value.body)
        if (clozes.isEmpty()) {
            _state.update {
                it.copy(ai = AiState.Result("Cloze cards", "No redacted spans yet — use Redact mode to mark the parts worth hiding, then try again."))
            }
            return
        }
        _state.update { it.copy(ai = AiState.Loading("Making cloze cards")) }
        viewModelScope.launch {
            try {
                ensureSaved()
                clozes.forEach { c -> reviews.addCard(_state.value.id, c.front, c.back, aiGenerated = false) }
                _state.update {
                    it.copy(ai = AiState.Result("Cloze cards", "Created ${clozes.size} cloze card${if (clozes.size == 1) "" else "s"} from your redactions — see Practise."))
                }
            } catch (e: Exception) {
                Log.e("NoteEditor", "cloze cards failed", e)
                _state.update { it.copy(ai = AiState.Error(e.message ?: "Couldn't create the cards.")) }
            }
        }
    }

    /** Generate flashcards from just the selected excerpt, attached to this note. */
    fun cardsFromSelection(excerpt: String) {
        val text = excerpt.trim()
        if (text.isBlank()) return
        aiAction("Making cards") {
            ensureSaved()
            ai.generateCards(currentNote().copy(body = text)).map {
                AiState.Result("Flashcards", "Created $it card${if (it == 1) "" else "s"} from the selection.")
            }
        }
    }

    // ── Note analysis (colour-coded annotation overlay) ──────────────────────────────────────

    /** Analyse the note into colour-coded annotations shown in an overlay (nothing is written). */
    fun analyzeNote() {
        if (_state.value.analysis is AnalysisState.Loading) return
        val note = currentNote()
        if (note.body.isBlank()) {
            _state.update { it.copy(analysis = AnalysisState.Error("Write something first, then analyse.")) }
            return
        }
        _state.update { it.copy(analysis = AnalysisState.Loading) }
        viewModelScope.launch {
            ai.analyze(note).fold(
                onSuccess = { anns ->
                    _state.update {
                        it.copy(
                            analysis = if (anns.isEmpty()) AnalysisState.Error("No annotations found.")
                            else AnalysisState.Ready(anns),
                        )
                    }
                },
                onFailure = { e -> _state.update { it.copy(analysis = AnalysisState.Error(e.message ?: "Analysis failed.")) } },
            )
        }
    }

    fun dismissAnalysis() = _state.update { it.copy(analysis = AnalysisState.Idle) }

    /** Render the analysis as an appended "## Annotations" Markdown section over the current body. */
    private fun annotatedMarkdown(): String {
        val anns = (_state.value.analysis as? AnalysisState.Ready)?.annotations.orEmpty()
        if (anns.isEmpty()) return _state.value.body
        return buildString {
            append(_state.value.body.trimEnd())
            append("\n\n---\n## Annotations\n")
            anns.forEach { a -> append("- **[${a.category}]** “${a.excerpt}” — ${a.comment}\n") }
        }
    }

    /** Save the annotated version as a NEW note, leaving the original untouched. */
    fun saveAnnotatedCopy() {
        val s = _state.value
        val body = annotatedMarkdown()
        viewModelScope.launch {
            try {
                val title = "${s.title.ifBlank { "Untitled" }} (annotated)"
                val id = notes.upsert(Note(title = title, body = body, tags = mergeTags(s.tagsText, body)))
                graph.ensureNodeForNote(id, title)
                _state.update {
                    it.copy(analysis = AnalysisState.Idle, ai = AiState.Result("Saved", "Annotated copy saved as \"$title\"."))
                }
            } catch (e: Exception) {
                Log.e("NoteEditor", "save annotated copy failed", e)
                _state.update { it.copy(analysis = AnalysisState.Error(e.message ?: "Couldn't save the copy.")) }
            }
        }
    }

    /** Write the annotated version over the current note. */
    fun overwriteWithAnnotated() {
        _state.update { it.copy(body = annotatedMarkdown(), analysis = AnalysisState.Idle) }
        save()
    }

    private fun aiAction(label: String, block: suspend () -> Result<AiState>) {
        _state.update { it.copy(ai = AiState.Loading(label)) }
        viewModelScope.launch {
            val result = runCatching { block().getOrThrow() }
            _state.update {
                it.copy(ai = result.getOrElse { e -> AiState.Error(e.message ?: "Something went wrong.") })
            }
        }
    }

    private suspend fun ensureSaved() {
        if (_state.value.id <= 0) {
            val s = _state.value
            val id = notes.upsert(Note(title = s.title, body = s.body, tags = mergeTags(s.tagsText, s.body)))
            histories.adopt(id, history)
            graph.ensureNodeForNote(id, s.title)
            _state.update { it.copy(id = id) }
        }
    }

    private fun currentNote(): Note = _state.value.let {
        Note(id = it.id, title = it.title, body = it.body, tags = mergeTags(it.tagsText, it.body))
    }

    private fun mergeTags(tagsText: String, body: String): List<String> {
        val explicit = tagsText.split(',', ' ', '\n').map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }
        val inline = Regex("#(\\w+)").findAll(body).map { it.groupValues[1] }.toList()
        // Obsidian-style frontmatter `tags:` properties count as real tags too (shown in the
        // editor's tag row and persisted, so search/groups/graph all see them).
        val frontmatter = com.generic.mnemos.core.notes.Frontmatter.tags(body)
        return (explicit + inline + frontmatter).distinct()
    }
}
