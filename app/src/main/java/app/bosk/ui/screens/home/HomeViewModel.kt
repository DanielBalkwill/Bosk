package app.bosk.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bosk.core.ai.NoteAiService
import app.bosk.core.ai.RoadmapStep
import app.bosk.core.ai.DailyRoadmap
import app.bosk.core.ai.llm.LlmClient
import app.bosk.core.graph.GraphRepository
import app.bosk.core.settings.SettingsRepository
import app.bosk.core.graph.GraphSnapshot
import app.bosk.core.notes.ActiveNoteStore
import app.bosk.core.notes.Mentions
import app.bosk.core.notes.Note
import app.bosk.core.notes.NotesRepository
import app.bosk.core.notes.Redaction
import app.bosk.core.review.ReviewRepository
import app.bosk.service.ambient.AmbientService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row in "Today's Queue". */
data class QueueItem(val reviewId: Long, val noteId: Long, val front: String, val back: String)

/** The note shown in the "Now Reading" panel. */
data class NowReading(val id: Long, val title: String, val tag: String, val preview: String)

data class HomeState(
    val dueCount: Int = 0,
    val noteCount: Int = 0,
    val nowReading: NowReading? = null,
    val queue: List<QueueItem> = emptyList(),
    val aiReply: String? = null,
    val aiBusy: Boolean = false,
    val roadmapSteps: List<RoadmapStep> = emptyList(),
    val roadmapBusy: Boolean = false,
    val roadmapError: String? = null,
    /** Optimistic playback state for the Now Playing control (kept in sync with play/pause/speak). */
    val isPlaying: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reviews: ReviewRepository,
    private val notes: NotesRepository,
    private val llm: LlmClient,
    private val noteAi: NoteAiService,
    private val settings: SettingsRepository,
    private val activeNote: ActiveNoteStore,
    graph: GraphRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    /** Full knowledge graph — the home preview centres it on the current note's neighbourhood. */
    val graphSnapshot: StateFlow<GraphSnapshot> =
        graph.observeGraph().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GraphSnapshot())

    /** All notes — drives the `#mention` autocomplete + colouring in the command bar. */
    val allNotes: StateFlow<List<Note>> =
        notes.observeNotes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True while there's a previously-previewed note to step back to (drives Home's back button). */
    val canGoBack: StateFlow<Boolean> =
        activeNote.history.map { it.size >= 2 }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Recently-opened notes (newest first) — Home's "Jump back in" quick-resume row. */
    val recentNotes: StateFlow<List<Note>> =
        combine(activeNote.recent, notes.observeNotes()) { ids, list ->
            val byId = list.associateBy { it.id }
            ids.mapNotNull { byId[it] }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** One-shot "open this note in the editor" events (voice capture lands you in the new note). */
    private val _openNote = kotlinx.coroutines.flow.MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val openNoteEvents: kotlinx.coroutines.flow.SharedFlow<Long> = _openNote

    /** Save dictated speech as a new note and open it (Home's mic quick-capture). */
    fun captureVoice(text: String) {
        val body = text.trim()
        if (body.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val title = body.substringBefore('\n').take(48)
                val id = notes.upsert(Note(title = title, body = body, tags = listOf("voice")))
                activeNote.set(id)
                _openNote.tryEmit(id)
            }
        }
    }

    init {
        // Show today's saved roadmap (if any) without regenerating. A roadmap from a previous day
        // is ignored, so the card invites a fresh "Generate".
        viewModelScope.launch {
            val saved = DailyRoadmap.decode(settings.dailyRoadmapJson.first())
            if (saved != null && saved.isForDay(todayEpochDay())) {
                _state.update { it.copy(roadmapSteps = saved.steps) }
            }
        }
        viewModelScope.launch {
            // The focused note follows the app-wide ActiveNoteStore (set when a note is opened in
            // the editor). On a cold open with nothing focused yet, seed it with a RANDOM note so
            // Home always shows — and can open — something instead of a blank panel.
            combine(reviews.observeDueCount(), notes.observeNotes(), activeNote.id) { due, list, aid ->
                Triple(due, list, aid)
            }.collect { (due, list, aid) ->
                val current = when {
                    list.isEmpty() -> null
                    else -> list.firstOrNull { it.id == aid }
                        ?: list.random().also { activeNote.set(it.id) }
                }
                val nowReading = current?.let { n ->
                    NowReading(
                        id = n.id,
                        title = n.title.ifBlank { "Untitled note" },
                        tag = n.tags.take(2).joinToString(" ") { "#$it" },
                        preview = Redaction.stripMarkers(n.body).ifBlank { "Open to start writing…" },
                    )
                }
                _state.update { it.copy(dueCount = due, noteCount = list.size, nowReading = nowReading) }
                refreshQueue()
            }
        }
    }

    /** Cycle the focused note (also re-centres the home mini-graph + drives the active note). */
    fun previousNote() = step(-1)
    fun nextNote() = step(+1)

    private fun step(delta: Int) {
        val list = allNotes.value
        if (list.isEmpty()) return
        val i = list.indexOfFirst { it.id == activeNote.id.value }.coerceAtLeast(0)
        val next = (i + delta).coerceIn(0, list.lastIndex)
        activeNote.set(list[next].id)
    }

    private fun refreshQueue() {
        viewModelScope.launch {
            val cards = runCatching { reviews.dueCards(limit = 6) }.getOrDefault(emptyList())
            _state.update {
                it.copy(queue = cards.map { c -> QueueItem(c.id, c.noteId, c.front, c.back) })
            }
        }
    }

    /**
     * The flagship command bar: route free text to the active AI provider, then speak it.
     * Any `#Note Name` mention folds that note's contents into the prompt as context.
     */
    fun runCommand(text: String) {
        val q = text.trim()
        if (q.isBlank() || _state.value.aiBusy) return
        _state.update { it.copy(aiBusy = true, aiReply = null) }
        viewModelScope.launch {
            val prompt = buildPrompt(q)
            llm.generate(prompt).fold(
                onSuccess = { reply ->
                    val clean = reply.trim()
                    // Speaking the reply starts playback, so reflect that in the play/pause control.
                    _state.update { it.copy(aiBusy = false, aiReply = clean, isPlaying = it.isPlaying || clean.isNotBlank()) }
                    if (clean.isNotBlank()) AmbientService.speak(context, clean.take(600))
                },
                onFailure = { e ->
                    _state.update { it.copy(aiBusy = false, aiReply = e.message ?: "Couldn't reach the AI.") }
                },
            )
        }
    }

    /** Prepend the contents of any `#Note` mentioned in [text] as grounding context. */
    private fun buildPrompt(text: String): String {
        val notes = allNotes.value
        if (notes.isEmpty()) return text
        val matches = Mentions.find(text, notes.map { it.title })
        if (matches.isEmpty()) return text
        val context = buildString {
            matches.map { it.title }.distinct().forEach { title ->
                notes.firstOrNull { it.title.equals(title, ignoreCase = true) }?.let { n ->
                    appendLine("Referenced note \"${n.title}\":")
                    appendLine(Redaction.forAi(n.body))
                    appendLine()
                }
            }
        }
        return "$context---\n$text"
    }

    fun dismissReply() = _state.update { it.copy(aiReply = null) }

    /**
     * Ask the AI for a structured, time-blocked daily roadmap from today's due count + recent topics,
     * then persist it for the day (so revisiting Home shows it without regenerating).
     */
    fun generateRoadmap() {
        if (_state.value.roadmapBusy) return
        _state.update { it.copy(roadmapBusy = true, roadmapError = null) }
        viewModelScope.launch {
            val topics = allNotes.value.take(12)
                .map { it.title.ifBlank { "Untitled" } }
                .filter { it.isNotBlank() }
            noteAi.generateRoadmap(_state.value.dueCount, topics).fold(
                onSuccess = { steps ->
                    _state.update { it.copy(roadmapBusy = false, roadmapSteps = steps, roadmapError = null) }
                    if (steps.isNotEmpty()) {
                        val today = todayEpochDay()
                        settings.saveDailyRoadmap(DailyRoadmap.encode(DailyRoadmap(steps, today)), today)
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(roadmapBusy = false, roadmapError = e.message ?: "Couldn't build a roadmap.") }
                },
            )
        }
    }

    fun dismissRoadmap() = _state.update { it.copy(roadmapSteps = emptyList(), roadmapError = null) }

    private fun todayEpochDay(): Long = java.time.LocalDate.now().toEpochDay()

    fun play() { AmbientService.play(context); _state.update { it.copy(isPlaying = true) } }
    fun pause() { AmbientService.pause(context); _state.update { it.copy(isPlaying = false) } }

    /** Focus a specific note (e.g. tapping an upcoming queue item in the expanded Now Reading panel). */
    fun selectNote(id: Long) { if (id > 0) activeNote.set(id) }

    /** Step the Home preview back to the previously-previewed note; false if there's nowhere to go. */
    fun goBack(): Boolean = activeNote.back()

    /** Skip advances the focused note too, so the preview + mini-graph follow the prominent
     *  "next" control as well as the dedicated Now Reading arrow. Does NOT auto-start playback —
     *  only a sent AI prompt reads aloud (and updates the control). */
    fun skip() {
        nextNote()
        AmbientService.skip(context)
    }
}
