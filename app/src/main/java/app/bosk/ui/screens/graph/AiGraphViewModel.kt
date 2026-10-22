package app.bosk.ui.screens.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bosk.core.ai.NoteAiService
import app.bosk.core.graph.GraphRepository
import app.bosk.core.graph.GraphSnapshot
import app.bosk.core.notes.CompilerStore
import app.bosk.core.notes.NotesRepository
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
 * Backs the "AI graph" sub-tab: an AI-assisted graph MAKER. The learner describes a topic in
 * plain language; the AI derives search terms, the vault is scanned for matching notes (naive
 * contains + FTS), and a fresh scoped graph is drawn from those nodes, their 1-hop neighbours,
 * and the edges among them. Each new prompt REPLACES the graph (it's a maker, not an accumulator
 * — unlike the flashcard graph's additive prompt).
 */
@HiltViewModel
class AiGraphViewModel @Inject constructor(
    private val graph: GraphRepository,
    private val notes: NotesRepository,
    private val ai: NoteAiService,
    private val compiler: CompilerStore,
) : ViewModel() {

    /** Notes the current prompt matched (the scoped graph's seed set). */
    private val _matchedNoteIds = MutableStateFlow<Set<Long>>(emptySet())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    /** The prompt that produced the current graph (shown as its title), or null when empty. */
    private val _lastQuery = MutableStateFlow<String?>(null)
    val lastQuery: StateFlow<String?> = _lastQuery.asStateFlow()

    private val _selectedNodeId = MutableStateFlow<Long?>(null)
    val selectedNodeId: StateFlow<Long?> = _selectedNodeId.asStateFlow()
    fun select(id: Long?) { _selectedNodeId.value = id }

    /**
     * Matches-only (default ON): show just the notes the prompt matched — no pulled-in 1-hop
     * neighbours. Toggle OFF to widen the view to the surrounding context.
     */
    private val _matchedOnly = MutableStateFlow(true)
    val matchedOnly: StateFlow<Boolean> = _matchedOnly.asStateFlow()
    fun toggleMatchedOnly() { _matchedOnly.value = !_matchedOnly.value }

    /** Matched notes' nodes (+ their 1-hop neighbours unless matches-only), edges among that set. */
    val snapshot: StateFlow<GraphSnapshot> =
        combine(graph.observeGraph(), _matchedNoteIds, _matchedOnly) { full, ids, matchedOnly ->
            if (ids.isEmpty()) GraphSnapshot()
            else {
                val seed = full.nodes
                    .filter { it.noteId != null && it.noteId in ids }
                    .map { it.id }
                    .toHashSet()
                val keep = HashSet(seed)
                if (!matchedOnly) {
                    val adj = HashMap<Long, MutableList<Long>>()
                    full.edges.forEach { e ->
                        adj.getOrPut(e.sourceId) { mutableListOf() }.add(e.targetId)
                        adj.getOrPut(e.targetId) { mutableListOf() }.add(e.sourceId)
                    }
                    seed.forEach { id -> adj[id]?.let { keep.addAll(it) } }
                }
                GraphSnapshot(
                    nodes = full.nodes.filter { it.id in keep },
                    edges = full.edges.filter { it.sourceId in keep && it.targetId in keep },
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GraphSnapshot())

    /** Node ids of the directly-matched notes — drawn brighter than pulled-in neighbours. */
    val matchedNodeIds: StateFlow<Set<Long>> =
        combine(snapshot, _matchedNoteIds) { snap, ids ->
            snap.nodes.filter { it.noteId != null && it.noteId in ids }.map { it.id }.toSet()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Build a fresh scoped graph from a free-text [prompt]. */
    fun build(prompt: String) {
        val q = prompt.trim()
        if (q.isBlank() || _busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val all = notes.observeNotes().first()
            val terms = ai.deriveSearchQueries(q).getOrDefault(listOf(q)).ifEmpty { listOf(q) }
            val hits = LinkedHashSet<Long>()
            terms.forEach { t ->
                all.filter { n ->
                    n.title.contains(t, true) || n.body.contains(t, true) ||
                        n.tags.any { tag -> tag.contains(t, true) }
                }.forEach { hits.add(it.id) }
                // FTS catches prefix/stem matches the naive contains() misses.
                runCatching { notes.search(t) }.getOrDefault(emptyList()).forEach { hits.add(it.id) }
            }
            if (hits.isEmpty()) {
                _message.value = "No notes matched \"$q\" — try different wording."
            } else {
                _matchedNoteIds.value = hits
                _lastQuery.value = q
                _selectedNodeId.value = null
                _message.value = "Graph built: ${hits.size} matching note${if (hits.size == 1) "" else "s"} (+ neighbours)."
            }
            _busy.value = false
        }
    }

    /** Empty the maker back to its prompt-first start state. */
    fun clear() {
        _matchedNoteIds.value = emptySet()
        _lastQuery.value = null
        _selectedNodeId.value = null
    }

    // ── Result actions: turn a built graph into a workflow, not just a picture ─────────────────

    /** Queue every matched note in the Compiler (they're then attachable in the tutor too). */
    fun sendToCompiler() {
        val ids = _matchedNoteIds.value
        if (ids.isEmpty()) return
        compiler.addAll(ids)
        _message.value = "Sent ${ids.size} note${if (ids.size == 1) "" else "s"} to Compiler."
    }

    /** Generate flashcards for the matched notes (capped — AI call per note). */
    fun makeCards(limit: Int = CARDS_NOTE_CAP) {
        val ids = _matchedNoteIds.value.toList().take(limit)
        if (ids.isEmpty() || _busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val byId = notes.observeNotes().first().associateBy { it.id }
            var made = 0
            var covered = 0
            ids.forEach { id ->
                val n = byId[id] ?: return@forEach
                covered++
                made += ai.generateCards(n).getOrDefault(0)
            }
            _message.value =
                if (made == 0) "No cards created — check your AI provider."
                else "Created $made card${if (made == 1) "" else "s"} across $covered note${if (covered == 1) "" else "s"} — see Practise."
            _busy.value = false
        }
    }

    companion object {
        /** Most matched notes to run card generation over in one go (one AI call each). */
        const val CARDS_NOTE_CAP = 8
    }
}
