package app.bosk.ui.screens.graph

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bosk.core.ai.NoteAiService
import app.bosk.core.cache.SessionCache
import app.bosk.core.graph.GraphRepository
import app.bosk.core.graph.GraphSnapshot
import app.bosk.core.notes.ActiveNoteStore
import app.bosk.core.notes.CompilerStore
import app.bosk.core.notes.Frontmatter
import app.bosk.core.notes.Note
import app.bosk.core.notes.NotesRepository
import app.bosk.core.notes.WikiLinkSyncer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import app.bosk.core.settings.GraphGroup
import app.bosk.core.settings.OverviewLayout
import app.bosk.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One titled AI overview, for the "All overviews" digest panel. */
data class OverviewEntry(val noteId: Long, val title: String, val summary: String)

@HiltViewModel
class GraphViewModel @Inject constructor(
    private val graph: GraphRepository,
    private val notes: NotesRepository,
    private val ai: NoteAiService,
    private val wikiLinks: WikiLinkSyncer,
    private val activeNote: ActiveNoteStore,
    private val settings: SettingsRepository,
    private val compiler: CompilerStore,
    private val cache: SessionCache,
    reviews: app.bosk.core.review.ReviewRepository,
) : ViewModel() {

    // ── Knowledge health: colour nodes by memory strength ("the graph IS the curriculum") ─────
    private val _healthMode = MutableStateFlow(false)
    val healthMode: StateFlow<Boolean> = _healthMode.asStateFlow()
    fun toggleHealthMode() { _healthMode.value = !_healthMode.value }

    /** noteId → FSRS aggregates (total/due/avg stability + 0..1 score) for health colouring. */
    val noteHealth: StateFlow<Map<Long, app.bosk.core.review.NoteHealth>> =
        reviews.observeNoteHealth()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** A note's effective colour-tags: its #tags PLUS Obsidian-style frontmatter `tags:` properties. */
    private fun Note.colourTags(): List<String> = (tags + Frontmatter.tags(body)).distinct()

    /** All tags in the user's notes, most-frequent first — orders/suggests graph colour groups. */
    val allTags: StateFlow<List<String>> =
        notes.observeNotes().map { list ->
            val freq = HashMap<String, Int>()
            list.forEach { it.colourTags().forEach { t -> freq[t] = (freq[t] ?: 0) + 1 } }
            freq.entries.sortedByDescending { it.value }.map { it.key }
        }
            .flowOn(Dispatchers.Default) // whole-vault tag frequency pass — off the main thread
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The most-recently focused note (graph opens zoomed/selected on its node). */
    val activeNoteId: StateFlow<Long?> = activeNote.id

    // ── Focus on open: a chosen tag/group ("" = everything) + whether the choice was made ─────────
    val focusTag: StateFlow<String> =
        settings.graphFocusTag.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
    // Initial = true so returning users never see the chooser flash; a first-time user's stored
    // `false` arrives a frame later and shows it.
    val focusChosen: StateFlow<Boolean> =
        settings.graphFocusChosen.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val openZoomRecent: StateFlow<Boolean> =
        settings.graphOpenZoomRecent.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Choose a focus tag ("" = everything) and remember the choice so the chooser doesn't reappear. */
    fun setFocus(tag: String) {
        viewModelScope.launch {
            settings.setGraphFocusTag(tag.trim())
            settings.setGraphFocusChosen(true)
        }
    }
    /** Re-show the first-open focus chooser. */
    fun reopenFocusChooser() { viewModelScope.launch { settings.setGraphFocusChosen(false) } }
    fun setOpenZoomRecent(on: Boolean) { viewModelScope.launch { settings.setGraphOpenZoomRecent(on) } }

    // ── Per-note AI summaries (shown in a box under the selected node) ────────────────────────
    private val _summaries = MutableStateFlow<Map<Long, String>>(emptyMap())
    val summaries: StateFlow<Map<Long, String>> = _summaries.asStateFlow()
    private val summaryBusy = HashSet<Long>()

    fun requestSummary(noteId: Long) {
        if (noteId <= 0 || noteId in _summaries.value || noteId in summaryBusy) return
        summaryBusy.add(noteId)
        viewModelScope.launch {
            val note = notes.get(noteId)
            val text = note?.let { ai.summarise(it).getOrElse { e -> e.message ?: "(couldn't summarise)" } }
                ?.trim().orEmpty().ifBlank { "(no summary)" }
            _summaries.value = _summaries.value + (noteId to text)
            summaryBusy.remove(noteId)
        }
    }

    /** Every AI overview generated this session, titled — powers the "All overviews" digest panel. */
    val overviewDigest: StateFlow<List<OverviewEntry>> =
        combine(_summaries, notes.observeNotes()) { sums, all ->
            val byId = all.associateBy { it.id }
            sums.entries
                .map { (id, text) -> OverviewEntry(id, byId[id]?.title?.ifBlank { "Untitled" } ?: "Note $id", text) }
                .sortedBy { it.title.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Overview mode (sticky multi-select with summaries; can send to the Compiler) ──────────
    private val _overviewMode = MutableStateFlow(false)
    val overviewMode: StateFlow<Boolean> = _overviewMode.asStateFlow()
    private val _overviewPinned = MutableStateFlow<Set<Long>>(emptySet())
    val overviewPinned: StateFlow<Set<Long>> = _overviewPinned.asStateFlow()

    fun toggleOverviewMode() {
        _overviewMode.value = !_overviewMode.value
        if (!_overviewMode.value) _overviewPinned.value = emptySet()
    }

    /** Remove a single node from the Overview pin set (its summary box closes). */
    fun unpin(nodeId: Long) { _overviewPinned.value = _overviewPinned.value - nodeId }

    fun sendOverviewToCompiler() {
        val noteIds = _overviewPinned.value.mapNotNull { id -> snapshot.value.nodes.firstOrNull { it.id == id }?.noteId }
        if (noteIds.isEmpty()) { _message.value = "Tap nodes in Overview mode first."; return }
        compiler.addAll(noteIds)
        _message.value = "Sent ${noteIds.size} note(s) to Compiler."
        _overviewPinned.value = emptySet()
    }

    fun sendSelectedToCompiler() {
        val noteId = snapshot.value.nodes.firstOrNull { it.id == _selectedNodeId.value }?.noteId
        if (noteId == null) { _message.value = "This node has no linked note."; return }
        compiler.add(noteId)
        _message.value = "Sent to Compiler."
    }

    /** noteId for the selected node, if any (lets the screen offer "Open note"). */
    fun noteIdForNode(nodeId: Long?): Long? =
        nodeId?.let { id -> snapshot.value.nodes.firstOrNull { it.id == id }?.noteId }

    // ── Graph settings: label render distance + colour groups ─────────────────────────────────
    val labelMinScale: StateFlow<Float> =
        settings.graphLabelMinScale.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            app.bosk.core.settings.DEFAULT_LABEL_MIN_SCALE,
        )
    val groups: StateFlow<List<GraphGroup>> =
        settings.graphGroups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Overview synopsis arrangement: tiled near nodes (default), radial ring, side list, or grid. */
    val overviewLayout: StateFlow<OverviewLayout> =
        settings.graphOverviewLayout.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewLayout.TILED)
    fun setOverviewLayout(layout: OverviewLayout) { viewModelScope.launch { settings.setGraphOverviewLayout(layout) } }

    fun setLabelMinScale(scale: Float) { viewModelScope.launch { settings.setGraphLabelMinScale(scale) } }

    /** Label text size multiplier (labels also shrink with zoom-out). */
    val labelSizeMult: StateFlow<Float> =
        settings.graphLabelSizeMult.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)
    fun setLabelSizeMult(mult: Float) { viewModelScope.launch { settings.setGraphLabelSizeMult(mult) } }

    /** Opening settle motion: 0 = open frozen … 1 = full re-layout animation. */
    val settleTemp: StateFlow<Float> =
        settings.graphSettleTemp.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.35f)
    fun setSettleTemp(temp: Float) { viewModelScope.launch { settings.setGraphSettleTemp(temp) } }
    fun addGroup(tag: String, colorArgb: Int) {
        val clean = tag.trim().removePrefix("#")
        if (clean.isBlank()) return
        viewModelScope.launch {
            val next = groups.value.filterNot { it.tag.equals(clean, true) } + GraphGroup(clean, colorArgb)
            settings.setGraphGroups(next)
        }
    }
    fun removeGroup(tag: String) {
        viewModelScope.launch { settings.setGraphGroups(groups.value.filterNot { it.tag.equals(tag, true) }) }
    }

    /** Reorder a colour group (list order = priority; the topmost match wins for a node's colour). */
    fun moveGroup(tag: String, up: Boolean) {
        val list = groups.value.toMutableList()
        val i = list.indexOfFirst { it.tag.equals(tag, true) }
        if (i < 0) return
        val j = if (up) i - 1 else i + 1
        if (j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        viewModelScope.launch { settings.setGraphGroups(list) }
    }

    // ── Colour-group presets (5 saveable full-graph colourings; default = the first) ─────────────
    val activePreset: StateFlow<Int> =
        settings.graphActivePreset.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val groupPresets: StateFlow<List<List<GraphGroup>>> =
        settings.graphGroupPresets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun selectPreset(index: Int) { viewModelScope.launch { settings.setActivePreset(index) } }

    /**
     * Auto-assign colour groups to the 8 most-connected tags (high→low priority). Computed directly
     * from the graph's connection counts — no AI tokens spent, which is faster and keeps it cheap on
     * mobile (the ranking IS a deterministic count, so AI would add cost without adding accuracy).
     */
    fun autoAssignGroups() {
        viewModelScope.launch {
            val snap = snapshot.value
            val notesById = notes.observeNotes().first().associateBy { it.id }
            val degree = HashMap<Long, Int>()
            snap.edges.forEach {
                degree[it.sourceId] = (degree[it.sourceId] ?: 0) + 1
                degree[it.targetId] = (degree[it.targetId] ?: 0) + 1
            }
            val tagConnections = HashMap<String, Int>()
            snap.nodes.forEach { node ->
                val d = degree[node.id] ?: 0
                val tags = node.noteId?.let { notesById[it] }?.colourTags().orEmpty()
                tags.forEach { t -> tagConnections[t] = (tagConnections[t] ?: 0) + d }
            }
            val top = tagConnections.entries.sortedByDescending { it.value }.take(8).map { it.key }
            if (top.isEmpty()) { _message.value = "No connected tags to colour yet."; return@launch }
            val palette = autoPalette(top.size)
            val assigned = top.mapIndexed { i, tag -> GraphGroup(tag, palette[i]) }
            settings.setGraphGroups(assigned) // writes into the active preset; order = priority
            _message.value = "Coloured ${assigned.size} top tag(s) by connections."
        }
    }

    /** [n] visually-distinct colours, evenly spaced around the hue wheel. */
    private fun autoPalette(n: Int): List<Int> =
        (0 until n).map { i -> Color.hsv((i * 360f / n.coerceAtLeast(1)), 0.62f, 0.92f).toArgb() }

    /** The full graph — used for internal lookups (suggest/link) and the holistic view. */
    val snapshot: StateFlow<GraphSnapshot> =
        graph.observeGraph()
            // Coalesce bursts of emissions — e.g. "Load graph" runs wikiLinks.sync per note, firing
            // observeGraph once per write. Debouncing collapses that storm into a single re-seed +
            // redraw once the writes settle, so a reload no longer spikes the frame time.
            .debounce(120)
            // Seed from the warm cache so re-entering the graph paints last-known nodes instantly
            // (no empty flash, no recompute) instead of an empty snapshot.
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), cache.graph)

    /** nodeId → group colour (argb), for nodes whose linked note carries a matching tag. */
    val nodeGroupColors: StateFlow<Map<Long, Int>> =
        // Derived from the debounced [snapshot] (one shared Room subscription, no recompute storm
        // while "Load graph" writes node-per-note) rather than a second raw observeGraph().
        combine(snapshot, notes.observeNotes(), settings.graphGroups) { snap, noteList, grp ->
            if (grp.isEmpty()) return@combine emptyMap<Long, Int>()
            val notesById = noteList.associateBy { it.id }
            buildMap {
                snap.nodes.forEach { node ->
                    val tags = node.noteId?.let { notesById[it] }?.colourTags().orEmpty()
                    // List order = priority: the topmost matching group wins (firstOrNull).
                    val match = grp.firstOrNull { g -> tags.any { it.equals(g.tag, true) } }
                    if (match != null) put(node.id, match.colorArgb)
                }
            }
        }
            .flowOn(Dispatchers.Default) // full node×tag pass — off the main thread (tab arrival)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun setQuery(q: String) { _query.value = q }

    /** Holistic = render EVERY node + edge (heavy). Off by default → only a focused neighbourhood. */
    private val _holistic = MutableStateFlow(false)
    val holistic: StateFlow<Boolean> = _holistic.asStateFlow()
    fun toggleHolistic() { _holistic.value = !_holistic.value }

    /**
     * Hop-limited focus, used by the note-connections view: show only nodes within 1 or 2 jumps
     * of the focused note. Null (the default, and always the case on the main Graph tab) keeps
     * the normal behaviour. Overrides the focus tag but NOT search or "Show all", so the graph
     * toolbar keeps its full functionality in that view.
     */
    private val _focusHops = MutableStateFlow<Int?>(null)
    val focusHops: StateFlow<Int?> = _focusHops.asStateFlow()
    fun setFocusHops(hops: Int?) { _focusHops.value = hops?.coerceIn(1, 3) }

    /**
     * What the renderer shows. Precedence:
     *  1. a search query → only matching nodes (label/tags/title/body), edges between survivors;
     *  2. holistic mode → the whole graph;
     *  3. default → a capped neighbourhood ([MAX_DEFAULT_NODES]) around the focused note, so a big
     *     graph never pays the O(n²) layout/draw cost unless the user asks for the full map.
     */
    // Focus + current-note bundled so visibleSnapshot stays within combine's 5-arg arity.
    private data class FocusState(val tag: String, val chosen: Boolean, val noteId: Long?, val hops: Int?)
    private val focusState =
        combine(settings.graphFocusTag, settings.graphFocusChosen, activeNote.id, _focusHops) {
            tag, chosen, aid, hops ->
            FocusState(tag, chosen, aid, hops)
        }

    val visibleSnapshot: StateFlow<GraphSnapshot> =
        combine(snapshot, notes.observeNotes(), _query, _holistic, focusState) {
            snap, noteList, q, holistic, fs ->
            when {
                q.isNotBlank() -> filter(snap, noteList, q)
                holistic -> snap // "Show all" still works with a hop limit set
                fs.hops != null -> hopNeighbourhood(snap, fs.noteId, fs.hops)
                fs.tag.isNotBlank() -> filterByTag(snap, noteList, fs.tag)
                else -> neighbourhood(snap, fs.noteId)
            }
        }
            // The BFS/filter above runs on every (re)subscription — i.e. exactly when the user
            // lands on the Graph tab. Off the main thread so arrival never stalls a frame.
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GraphSnapshot())

    /**
     * Exactly the nodes within [hops] jumps of the focused note's node (plus the edges among
     * them) — the note-connections view. Unlike [neighbourhood] there is no small-graph early
     * exit: 1 hop means 1 hop even in a 10-note vault. Falls back to the default neighbourhood
     * when the focused note has no node yet.
     */
    private fun hopNeighbourhood(snap: GraphSnapshot, focusedNoteId: Long?, hops: Int): GraphSnapshot {
        val start = snap.nodes.firstOrNull { it.noteId != null && it.noteId == focusedNoteId }?.id
            ?: return neighbourhood(snap, focusedNoteId)
        val adjacency = HashMap<Long, MutableList<Long>>(snap.nodes.size * 2)
        snap.edges.forEach { e ->
            adjacency.getOrPut(e.sourceId) { mutableListOf() }.add(e.targetId)
            adjacency.getOrPut(e.targetId) { mutableListOf() }.add(e.sourceId)
        }
        val depth = HashMap<Long, Int>().apply { put(start, 0) }
        val queue = ArrayDeque<Long>().apply { add(start) }
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val d = depth.getValue(cur)
            if (d >= hops) continue
            adjacency[cur]?.forEach { next ->
                if (next !in depth && depth.size < MAX_HOP_NODES) {
                    depth[next] = d + 1
                    queue.add(next)
                }
            }
        }
        return GraphSnapshot(
            nodes = snap.nodes.filter { it.id in depth },
            edges = snap.edges.filter { it.sourceId in depth && it.targetId in depth },
        )
    }

    /** Keep only nodes whose note carries [tag] (in #tags or frontmatter), and edges between them. */
    private fun filterByTag(snap: GraphSnapshot, noteList: List<Note>, tag: String): GraphSnapshot {
        val notesById = noteList.associateBy { it.id }
        val keptNodes = snap.nodes.filter { node ->
            val tags = node.noteId?.let { notesById[it] }?.colourTags().orEmpty()
            tags.any { it.equals(tag, true) }
        }
        val keptIds = keptNodes.mapTo(HashSet()) { it.id }
        val keptEdges = snap.edges.filter { it.sourceId in keptIds && it.targetId in keptIds }
        return GraphSnapshot(nodes = keptNodes, edges = keptEdges)
    }

    private fun filter(snap: GraphSnapshot, noteList: List<Note>, rawQuery: String): GraphSnapshot {
        val q = rawQuery.trim().lowercase()
        if (q.isEmpty()) return snap
        val notesById = noteList.associateBy { it.id }
        val keptNodes = snap.nodes.filter { node ->
            val note = node.noteId?.let { notesById[it] }
            // contains(ignoreCase) scans in place — no lowercased copy of every note body per keystroke.
            node.label.contains(q, ignoreCase = true) ||
                note?.tags?.any { it.contains(q, ignoreCase = true) } == true ||
                note?.title?.contains(q, ignoreCase = true) == true ||
                note?.body?.contains(q, ignoreCase = true) == true
        }
        val keptIds = keptNodes.mapTo(HashSet()) { it.id }
        val keptEdges = snap.edges.filter { it.sourceId in keptIds && it.targetId in keptIds }
        return GraphSnapshot(nodes = keptNodes, edges = keptEdges)
    }

    /**
     * A breadth-first neighbourhood of up to [MAX_DEFAULT_NODES] nodes around a focus node (the
     * node for the currently-focused note, else the most-connected node). Keeps the rendered set
     * small so the force simulation and Canvas stay cheap on large graphs.
     */
    private fun neighbourhood(snap: GraphSnapshot, focusedNoteId: Long?): GraphSnapshot {
        if (snap.nodes.size <= MAX_DEFAULT_NODES) return snap

        val adjacency = HashMap<Long, MutableList<Long>>(snap.nodes.size * 2)
        val degree = HashMap<Long, Int>(snap.nodes.size * 2)
        snap.edges.forEach { e ->
            adjacency.getOrPut(e.sourceId) { mutableListOf() }.add(e.targetId)
            adjacency.getOrPut(e.targetId) { mutableListOf() }.add(e.sourceId)
            degree[e.sourceId] = (degree[e.sourceId] ?: 0) + 1
            degree[e.targetId] = (degree[e.targetId] ?: 0) + 1
        }

        val start = snap.nodes.firstOrNull { it.noteId != null && it.noteId == focusedNoteId }?.id
            ?: degree.maxByOrNull { it.value }?.key
            ?: snap.nodes.first().id

        // BFS outward from the focus until we've gathered the cap.
        val kept = LinkedHashSet<Long>()
        val queue = ArrayDeque<Long>().apply { add(start) }
        kept.add(start)
        while (queue.isNotEmpty() && kept.size < MAX_DEFAULT_NODES) {
            val cur = queue.removeFirst()
            adjacency[cur]?.forEach { next ->
                if (kept.size < MAX_DEFAULT_NODES && kept.add(next)) queue.add(next)
            }
        }

        val keptNodes = snap.nodes.filter { it.id in kept }
        val keptEdges = snap.edges.filter { it.sourceId in kept && it.targetId in kept }
        return GraphSnapshot(nodes = keptNodes, edges = keptEdges)
    }

    private val _selectedNodeId = MutableStateFlow<Long?>(null)
    val selectedNodeId: StateFlow<Long?> = _selectedNodeId.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun select(nodeId: Long?) {
        _selectedNodeId.value = nodeId
        // Fetch an AI summary for the box under the node (just this note, not its neighbours).
        noteIdForNode(nodeId)?.let { requestSummary(it) }
        // In Overview mode a tap PINS the node (sticky — tapping elsewhere won't clear it).
        if (_overviewMode.value && nodeId != null) _overviewPinned.value = _overviewPinned.value + nodeId
    }
    fun consumeMessage() { _message.value = null }

    fun suggestConnectionsForSelected() {
        val nodeId = _selectedNodeId.value ?: return
        val node = snapshot.value.nodes.firstOrNull { it.id == nodeId } ?: return
        val noteId = node.noteId ?: run { _message.value = "This node has no linked note."; return }
        viewModelScope.launch {
            _busy.value = true
            val note = notes.get(noteId)
            val result = note?.let { ai.findConnections(it) }
            _message.value = when {
                note == null -> "Linked note not found."
                result!!.isSuccess -> "Suggested ${result.getOrDefault(0)} connection(s)."
                else -> result.exceptionOrNull()?.message ?: "Couldn't fetch suggestions."
            }
            _busy.value = false
        }
    }

    /**
     * Build the whole graph: ensure a node for every note and re-derive all `[[ ]]`/`!` link
     * edges from each note's body. Use this to backfill notes that predate live node-creation.
     */
    fun loadFullGraph() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val all = notes.observeNotes().first()
                all.forEach { wikiLinks.sync(it) }
                _message.value = "Graph loaded — ${all.size} note${if (all.size == 1) "" else "s"}."
            } catch (e: Exception) {
                _message.value = e.message ?: "Couldn't load the graph."
            } finally {
                _busy.value = false
            }
        }
    }

    fun confirmEdge(edgeId: Long) = viewModelScope.launch { graph.confirmEdge(edgeId) }
    fun deleteEdge(edgeId: Long) = viewModelScope.launch { graph.deleteEdge(edgeId) }

    /** Create a standalone concept node and select it. */
    fun addNode(label: String) {
        if (label.isBlank()) return
        viewModelScope.launch {
            val id = graph.addStandaloneNode(label)
            _selectedNodeId.value = id
            _message.value = "Added \"${label.trim()}\"."
        }
    }

    /** Manually link the selected node to [targetId] (a solid, user-authored edge). */
    fun linkSelectedTo(targetId: Long) {
        val source = _selectedNodeId.value ?: return
        if (source == targetId) return
        viewModelScope.launch {
            // aiGenerated=false, not a wikiLink → permanent and never reconciled away by note saves.
            graph.addEdge(source, targetId, label = "linked", strength = 1f, aiGenerated = false)
            _message.value = "Linked."
        }
    }

    fun deleteSelectedNode() {
        val id = _selectedNodeId.value ?: return
        viewModelScope.launch {
            graph.deleteNode(id)
            _selectedNodeId.value = null
        }
    }

    /** Persist the laid-out positions so reopening the graph restores them (near-zero movement). */
    fun savePositions(positions: Map<Long, Pair<Float, Float>>) {
        if (positions.isEmpty()) return
        viewModelScope.launch { runCatching { graph.saveNodePositions(positions) } }
    }

    companion object {
        /** Max nodes drawn in the default (non-holistic) focused view — keeps layout cheap. */
        const val MAX_DEFAULT_NODES = 30
        /** Safety cap for the hop-limited note-connections view (2 hops in a dense vault). */
        const val MAX_HOP_NODES = 80
    }
}
