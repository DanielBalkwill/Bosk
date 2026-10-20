package com.generic.mnemos.ui.screens.flashcardgraph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.generic.mnemos.core.ai.NoteAiService
import com.generic.mnemos.core.graph.GraphRepository
import com.generic.mnemos.core.graph.GraphSnapshot
import com.generic.mnemos.core.notes.NotesRepository
import com.generic.mnemos.core.review.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the green "flashcard graph": a SCOPED view of the vault graph that shows only the notes that
 * have flashcards, their directly-connected (similar) neighbours, and any extra notes the learner
 * pulls in via the AI prompt — never the whole vault. It reuses the same [GraphRepository] data and
 * the shared force-layout physics, just over a filtered node/edge set.
 */
@HiltViewModel
class FlashcardGraphViewModel @Inject constructor(
    private val graph: GraphRepository,
    private val notes: NotesRepository,
    private val reviews: ReviewRepository,
    private val ai: NoteAiService,
) : ViewModel() {

    /** Note ids that currently own at least one flashcard (the graph's "seed" set). */
    private val _cardNoteIds = MutableStateFlow<Set<Long>>(emptySet())

    /** Extra note ids the learner added through the AI prompt ("similar notes"). */
    private val _extraNoteIds = MutableStateFlow<Set<Long>>(emptySet())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    private val _selectedNodeId = MutableStateFlow<Long?>(null)
    val selectedNodeId: StateFlow<Long?> = _selectedNodeId.asStateFlow()
    fun select(id: Long?) { _selectedNodeId.value = id }

    init { refreshDecks() }

    private fun refreshDecks() {
        viewModelScope.launch {
            _cardNoteIds.value = runCatching { reviews.decks().map { it.noteId }.toSet() }.getOrDefault(emptySet())
        }
    }

    /**
     * The scoped graph: every node whose note has flashcards or was AI-suggested, PLUS their 1-hop
     * neighbours (so "similar"/connected notes show up too), with only the edges among that set.
     */
    val snapshot: StateFlow<GraphSnapshot> =
        combine(graph.observeGraph(), _cardNoteIds, _extraNoteIds) { full, cardIds, extra ->
            val seedNodeIds = full.nodes
                .filter { it.noteId != null && (it.noteId in cardIds || it.noteId in extra) }
                .map { it.id }
                .toHashSet()
            // 1-hop expansion via adjacency so connected (similar) notes are included.
            val adj = HashMap<Long, MutableList<Long>>()
            full.edges.forEach { e ->
                adj.getOrPut(e.sourceId) { mutableListOf() }.add(e.targetId)
                adj.getOrPut(e.targetId) { mutableListOf() }.add(e.sourceId)
            }
            val keep = HashSet(seedNodeIds)
            seedNodeIds.forEach { id -> adj[id]?.let { keep.addAll(it) } }
            GraphSnapshot(
                nodes = full.nodes.filter { it.id in keep },
                edges = full.edges.filter { it.sourceId in keep && it.targetId in keep },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GraphSnapshot())

    /** Node ids that are flashcard-owning notes — drawn brighter so they stand out from neighbours. */
    val flashcardNodeIds: StateFlow<Set<Long>> =
        combine(snapshot, _cardNoteIds) { snap, cardIds ->
            snap.nodes.filter { it.noteId != null && it.noteId in cardIds }.map { it.id }.toSet()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /**
     * Pull "similar notes" into the graph from a free-text prompt: derive search terms with the AI,
     * match them against the vault, and add the hits to the scope.
     */
    fun runPrompt(query: String) {
        val q = query.trim()
        if (q.isBlank() || _busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val all = notes.observeNotes().first()
            val terms = ai.deriveSearchQueries(q).getOrDefault(listOf(q)).ifEmpty { listOf(q) }
            val matched = all.filter { n -> terms.any { t -> n.title.contains(t, true) || n.body.contains(t, true) } }
                .map { it.id }
                .toSet()
            if (matched.isEmpty()) {
                _message.value = "No similar notes found for \"$q\"."
            } else {
                _extraNoteIds.value = _extraNoteIds.value + matched
                _message.value = "Added ${matched.size} similar note${if (matched.size == 1) "" else "s"}."
            }
            _busy.value = false
        }
    }
}
