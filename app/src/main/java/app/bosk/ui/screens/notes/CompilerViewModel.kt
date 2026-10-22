package app.bosk.ui.screens.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bosk.core.ai.NoteAiService
import app.bosk.core.notes.CompilerStore
import app.bosk.core.notes.Note
import app.bosk.core.notes.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A queued note as shown in the Compiler: the note, whether to show a summary, and the summary. */
data class CompilerRow(
    val note: Note,
    val withSummary: Boolean,
    val summary: String?,
    val summarising: Boolean,
)

@HiltViewModel
class CompilerViewModel @Inject constructor(
    private val store: CompilerStore,
    private val notes: NotesRepository,
    private val ai: NoteAiService,
    private val llm: app.bosk.core.ai.llm.LlmClient,
    private val wikiLinks: app.bosk.core.notes.WikiLinkSyncer,
) : ViewModel() {

    private val _summaries = MutableStateFlow<Map<Long, String>>(emptyMap())
    private val _busy = MutableStateFlow<Set<Long>>(emptySet())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    /** True while the whole-queue actions (Compile / cards-for-all) run. */
    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    /** Emits the id of a freshly-compiled note so the screen can open it. */
    private val _openCompiled = kotlinx.coroutines.flow.MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val openCompiled: kotlinx.coroutines.flow.SharedFlow<Long> = _openCompiled

    val rows: StateFlow<List<CompilerRow>> =
        combine(store.items, notes.observeNotes(), _summaries, _busy) { items, allNotes, summaries, busy ->
            val byId = allNotes.associateBy { it.id }
            items.mapNotNull { item ->
                val note = byId[item.noteId] ?: return@mapNotNull null
                CompilerRow(note, item.withSummary, summaries[item.noteId], item.noteId in busy)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Auto-summarise queued notes that asked for a summary and don't have one yet.
        viewModelScope.launch {
            combine(store.items, notes.observeNotes()) { items, allNotes ->
                val byId = allNotes.associateBy { it.id }
                items.filter { it.withSummary }.mapNotNull { byId[it.noteId] }
            }.collect { wanted ->
                wanted.forEach { note ->
                    if (note.id !in _summaries.value && note.id !in _busy.value) summarise(note)
                }
            }
        }
    }

    private fun summarise(note: Note) {
        _busy.update { it + note.id }
        viewModelScope.launch {
            val text = ai.summarise(note).getOrElse { "(couldn't summarise)" }.trim()
            _summaries.update { it + (note.id to text) }
            _busy.update { it - note.id }
        }
    }

    fun resummarise(noteId: Long) {
        viewModelScope.launch {
            val note = notes.get(noteId) ?: return@launch
            _summaries.update { it - noteId }
            summarise(note)
        }
    }

    fun remove(noteId: Long) = store.remove(noteId)
    fun toggleSummary(noteId: Long) = store.toggleSummary(noteId)
    fun clearAll() = store.clear()
    fun moveUp(noteId: Long) = store.move(noteId, up = true)
    fun moveDown(noteId: Long) = store.move(noteId, up = false)

    /**
     * THE compile: merge every queued note (in queue order) into one AI-synthesised study
     * document, saved as a new note that `[[links]]` back to its sources (so the graph shows the
     * synthesis connected to its parts). Emits the new note id for the screen to open.
     */
    fun compileAll() {
        val queued = rows.value.map { it.note }
        if (queued.isEmpty() || _working.value) return
        _working.value = true
        viewModelScope.launch {
            try {
                val prompt = buildString {
                    appendLine(
                        "Compile the following notes into ONE well-structured Markdown study document: " +
                            "a short overview, then the key ideas organised under headings, connections " +
                            "between the notes, and a final summary of what to remember. Where you draw on " +
                            "a specific source note, reference it as a wiki link like [[Its Title]]. " +
                            "Reply with the document body only.",
                    )
                    appendLine()
                    queued.forEach { n ->
                        appendLine("--- Note: ${n.title.ifBlank { "Untitled" }}")
                        appendLine(app.bosk.core.notes.Redaction.forAi(n.body).take(1500))
                    }
                }
                val body = llm.generate(prompt).getOrThrow().trim()
                val title = "Compiled: " + queued.take(3).joinToString(", ") { it.title.ifBlank { "Untitled" } }
                    .take(60) + if (queued.size > 3) " +${queued.size - 3}" else ""
                val note = Note(title = title, body = body, tags = listOf("compiled"))
                val id = notes.upsert(note)
                wikiLinks.sync(note.copy(id = id)) // graph edges to the [[source notes]]
                _message.value = "Compiled ${queued.size} notes → \"$title\"."
                _openCompiled.tryEmit(id)
            } catch (e: Exception) {
                _message.value = e.message ?: "Couldn't compile the queue."
            } finally {
                _working.value = false
            }
        }
    }

    /** Generate flashcards for EVERY queued note, one at a time (stops on first failure). */
    fun makeAllFlashcards() {
        val queued = rows.value.map { it.note }
        if (queued.isEmpty() || _working.value) return
        _working.value = true
        viewModelScope.launch {
            var made = 0
            var failed: String? = null
            for (n in queued) {
                val result = ai.generateCards(n)
                if (result.isSuccess) made += result.getOrDefault(0)
                else { failed = result.exceptionOrNull()?.message; break }
            }
            _message.value = when {
                failed != null && made == 0 -> failed
                failed != null -> "Created $made card(s), then failed: $failed"
                else -> "Created $made flashcard(s) across ${queued.size} notes — see Practise."
            }
            _working.value = false
        }
    }

    /** Turn a queued note into flashcards for the Practise tab. */
    fun makeFlashcards(noteId: Long) {
        _busy.update { it + noteId }
        viewModelScope.launch {
            val note = notes.get(noteId)
            val result = note?.let { ai.generateCards(it) }
            _message.value = when {
                note == null -> "Note not found."
                result!!.isSuccess -> "Created ${result.getOrDefault(0)} flashcard(s) — see Practise."
                else -> result.exceptionOrNull()?.message ?: "Couldn't create flashcards."
            }
            _busy.update { it - noteId }
        }
    }

    private inline fun <T> MutableStateFlow<T>.update(block: (T) -> T) { value = block(value) }
}
