package com.generic.mnemos.ui.screens.graph

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BubbleChart
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FilterCenterFocus
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.generic.mnemos.core.graph.GraphNode
import com.generic.mnemos.core.settings.OverviewLayout
import com.generic.mnemos.ui.components.rememberHaptics
import com.generic.mnemos.ui.navigation.LocalPageActive
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * The "active" local-graph set: nodes within [maxHops] hops of [start] (so only a moved node's
 * close neighbourhood reorients; everything further stays frozen). Capped at [cap] for safety on
 * dense hubs.
 */
private fun neighbourhoodOf(
    edges: List<com.generic.mnemos.core.graph.GraphEdge>,
    start: Long,
    maxHops: Int,
    cap: Int,
): Set<Long> {
    val adj = HashMap<Long, MutableList<Long>>()
    edges.forEach { e ->
        adj.getOrPut(e.sourceId) { mutableListOf() }.add(e.targetId)
        adj.getOrPut(e.targetId) { mutableListOf() }.add(e.sourceId)
    }
    val visited = LinkedHashSet<Long>().apply { add(start) }
    var frontier = listOf(start)
    repeat(maxHops) {
        if (visited.size >= cap || frontier.isEmpty()) return@repeat
        val next = ArrayList<Long>()
        frontier.forEach { cur ->
            adj[cur]?.forEach { nx -> if (visited.size < cap && visited.add(nx)) next.add(nx) }
        }
        frontier = next
    }
    return visited
}

// Kept deliberately modest: big blobs read as clutter, especially zoomed out on a dense vault.
private const val MIN_NODE_R = 2f
private const val MAX_NODE_R = 16f

/** Gold for AI-suggested (not-yet-confirmed) connections — visibly newer than established links. */
private val SuggestedEdgeColor = Color(0xFFE0A12E)

/** Max gap between two taps (down-to-down) that reads as a double-tap. */
private const val DOUBLE_TAP_WINDOW_MS = 350L

@Composable
fun GraphScreen(
    viewModel: GraphViewModel = hiltViewModel(),
    onOpenNote: (Long) -> Unit = {},
    onOpenCompiler: () -> Unit = {},
    onSwipePrev: () -> Unit = {},
    onSwipeNext: () -> Unit = {},
) {
    // Don't subscribe to the graph, run the force simulation, or draw until the page is actually
    // open. The sim is O(n²) per frame, so composing it as a pre-composed neighbour (or while
    // swiping past) was a major source of tab-switch lag. Off-screen → render nothing heavy.
    if (!LocalPageActive.current) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    val snapshot by viewModel.visibleSnapshot.collectAsStateWithLifecycle()
    val fullSnapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedNodeId.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val holistic by viewModel.holistic.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val labelMinScale by viewModel.labelMinScale.collectAsStateWithLifecycle()
    val labelSizeMult by viewModel.labelSizeMult.collectAsStateWithLifecycle()
    val settleTemp by viewModel.settleTemp.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val groupColors by viewModel.nodeGroupColors.collectAsStateWithLifecycle()
    val activePreset by viewModel.activePreset.collectAsStateWithLifecycle()
    val groupPresets by viewModel.groupPresets.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    val overviewMode by viewModel.overviewMode.collectAsStateWithLifecycle()
    val overviewPinned by viewModel.overviewPinned.collectAsStateWithLifecycle()
    val overviewLayout by viewModel.overviewLayout.collectAsStateWithLifecycle()
    val overviewDigest by viewModel.overviewDigest.collectAsStateWithLifecycle()
    val focusChosen by viewModel.focusChosen.collectAsStateWithLifecycle()
    val focusTag by viewModel.focusTag.collectAsStateWithLifecycle()
    val openZoomRecent by viewModel.openZoomRecent.collectAsStateWithLifecycle()
    val activeNoteId by viewModel.activeNoteId.collectAsStateWithLifecycle()
    val healthMode by viewModel.healthMode.collectAsStateWithLifecycle()
    val noteHealth by viewModel.noteHealth.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }

    var showLinkPicker by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showGroups by remember { mutableStateOf(false) }
    var showAllOverviews by remember { mutableStateOf(false) }
    var showAddNode by remember { mutableStateOf(false) }
    // Persistent across selections: once minimised, selecting another node keeps the card hidden
    // (a small "Show options" pill restores it); if not minimised, the card shows for the new node.
    var optionsMinimized by remember { mutableStateOf(false) }

    // Pan/zoom transform.
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasW by remember { mutableStateOf(0f) }
    var canvasH by remember { mutableStateOf(0f) }

    val nodeById = remember(snapshot) { snapshot.nodes.associateBy { it.id } }
    val degreeIds = remember(snapshot) {
        val s = HashSet<Long>()
        snapshot.edges.forEach { s.add(it.sourceId); s.add(it.targetId) }
        s
    }
    // Hot-loop draw data, resolved once per snapshot instead of per frame: each edge with its
    // endpoint nodes (no map lookups while drawing) and each node's base radius (no sqrt).
    val drawEdges = remember(snapshot) {
        snapshot.edges.mapNotNull { e ->
            val a = nodeById[e.sourceId] ?: return@mapNotNull null
            val b = nodeById[e.targetId] ?: return@mapNotNull null
            Triple(e, a, b)
        }
    }
    val baseRadius = remember(snapshot) {
        HashMap<Long, Float>(snapshot.nodes.size * 2).apply {
            snapshot.nodes.forEach { n -> put(n.id, 6f + sqrt(n.size) * 3f) }
        }
    }

    // Obsidian-style local graph: when a node is selected, ONLY its neighbourhood (≤30 hops away)
    // reorients; every other node is greyed out and frozen. Nothing selected → the whole visible
    // set is live.
    val activeSet: Set<Long>? = remember(snapshot, selectedId) {
        val sel = selectedId
        if (sel == null || snapshot.nodes.none { it.id == sel }) null
        else neighbourhoodOf(snapshot.edges, sel, maxHops = 3, cap = 60)
    }

    // Overview mode: pinned nodes + their 1-hop links, marked in a distinct colour (sticky).
    val overviewSet: Set<Long> = remember(snapshot, overviewPinned) {
        if (overviewPinned.isEmpty()) emptySet()
        else buildSet {
            addAll(overviewPinned)
            snapshot.edges.forEach { e ->
                if (e.sourceId in overviewPinned) add(e.targetId)
                if (e.targetId in overviewPinned) add(e.sourceId)
            }
        }
    }

    // Live force-directed layout — nodes drift like an elastic blob (Obsidian-style). It starts at
    // rest from saved/seeded positions (no constant background motion) and only moves on interaction.
    val nodeIds = remember(snapshot) { snapshot.nodes.map { it.id } }
    val edgePairs = remember(snapshot) { snapshot.edges.map { it.sourceId to it.targetId } }
    // In Overview mode we FREEZE the whole simulation (empty active set) and place each pinned node
    // by hand, just outside the cluster — so opening a summary box no longer triggers a graph-wide
    // reshuffle ("mass exodus"). Out of Overview, selection drives the usual local sim.
    val overviewActive = overviewMode && overviewPinned.isNotEmpty()
    val effectiveActive = if (overviewActive) emptySet<Long>() else activeSet
    val layout = rememberForceLayout(
        nodeIds = nodeIds,
        edges = edgePairs,
        activeIds = effectiveActive,
        settleOnSeed = true,
        settleInTemp = settleTemp,
        seed = { id -> nodeById[id]?.let { Offset(it.x, it.y) } ?: Offset.Zero },
    )
    val simPos = layout.positions

    // Push each pinned node just past the cluster's outer edge along its own direction (so its link
    // stretches out to reach it and the summary box gets clean room). Everything else stays put —
    // no mass movement. Original positions are remembered and restored when unpinned / Overview off.
    val prePinPositions = remember { mutableStateMapOf<Long, Offset>() }
    LaunchedEffect(overviewMode, overviewPinned, nodeIds, overviewLayout) {
        // Only the tiled layout displaces nodes; the other layouts leave the graph untouched
        // (radial/list/grid boxes live away from the cluster already).
        if (!overviewMode || overviewLayout != com.generic.mnemos.core.settings.OverviewLayout.TILED) {
            if (prePinPositions.isNotEmpty()) {
                prePinPositions.forEach { (id, p) -> simPos[id] = p }
                prePinPositions.clear()
            }
            return@LaunchedEffect
        }
        // Restore nodes that were unpinned since last time.
        prePinPositions.keys.toList().forEach { id ->
            if (id !in overviewPinned) prePinPositions.remove(id)?.let { simPos[id] = it }
        }
        if (overviewPinned.isEmpty()) return@LaunchedEffect
        // Cluster = the non-pinned nodes; measure its centre and absolute outer-edge distance (sim
        // space), then place pins a proportional bit beyond it — so the spread SCALES with the graph.
        val clusterPts = nodeIds.filter { it !in overviewPinned }.mapNotNull { simPos[it] }
        val centroid = if (clusterPts.isEmpty()) Offset.Zero else Offset(
            clusterPts.sumOf { it.x.toDouble() }.toFloat() / clusterPts.size,
            clusterPts.sumOf { it.y.toDouble() }.toFloat() / clusterPts.size,
        )
        val clusterRadius = clusterPts.maxOfOrNull { (it - centroid).getDistance() } ?: 120f
        val ring = clusterRadius * 1.3f + 40f
        val pinned = overviewPinned.toList()
        pinned.forEachIndexed { i, id ->
            if (id !in prePinPositions) {
                prePinPositions[id] = simPos[id] ?: (nodeById[id]?.let { Offset(it.x, it.y) } ?: Offset.Zero)
            }
            val raw = prePinPositions[id]!! - centroid
            val dist = raw.getDistance()
            val dir = if (dist < 1f) {
                val ang = 2.0 * Math.PI * i / pinned.size
                Offset(kotlin.math.cos(ang).toFloat(), kotlin.math.sin(ang).toFloat())
            } else raw / dist
            simPos[id] = centroid + dir * ring
        }
    }

    // On open, zoom in on & select the most-recently-focused note's node (configurable). Runs once
    // per screen entry, after the canvas is measured and positions are seeded.
    var didInitialFocus by remember { mutableStateOf(false) }
    LaunchedEffect(canvasW, snapshot, openZoomRecent) {
        if (didInitialFocus || !openZoomRecent || canvasW == 0f) return@LaunchedEffect
        val targetNoteId = activeNoteId ?: return@LaunchedEffect
        val node = snapshot.nodes.firstOrNull { it.noteId == targetNoteId } ?: return@LaunchedEffect
        val p = simPos[node.id] ?: return@LaunchedEffect
        scale = 1.8f
        offset = Offset(-p.x * scale, -p.y * scale) // centre the node (screenOf = centre + offset + p*scale)
        viewModel.select(node.id)
        didInitialFocus = true
    }

    // Auto-zoom in a little when summaries appear, giving the boxes room amongst the nodes.
    LaunchedEffect(overviewActive) {
        if (overviewActive && scale < 1.4f) {
            androidx.compose.animation.core.animate(
                initialValue = scale, targetValue = 1.6f,
                animationSpec = androidx.compose.animation.core.tween(500),
            ) { v, _ -> scale = v }
        }
    }

    // Persist positions when leaving the graph so reopening restores the layout (minimal motion) —
    // but with displaced pins restored, so Overview never scatters the saved layout.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            val toSave = layout.positions.toMutableMap()
            prePinPositions.forEach { (id, p) -> toSave[id] = p }
            viewModel.savePositions(toSave.mapValues { (_, p) -> p.x to p.y })
        }
    }

    // Base node radius (graph-space). Screen radius GROWS with zoom (× scale): zooming out makes
    // nodes small, zooming in makes them bigger — clamped to [MIN,MAX] so they stay tappable when
    // far out and never balloon when fully zoomed in.
    fun radiusOf(n: GraphNode) = baseRadius[n.id] ?: 6f
    fun screenRadiusOf(n: GraphNode) = (radiusOf(n) * scale).coerceIn(MIN_NODE_R, MAX_NODE_R)
    fun screenOf(n: GraphNode): Offset {
        val p = simPos[n.id] ?: Offset(n.x, n.y)
        return Offset(canvasW / 2f + offset.x + p.x * scale, canvasH / 2f + offset.y + p.y * scale)
    }
    // Screen position of a node id, usable from composition (for placing summary boxes).
    fun nodeScreenPos(nodeId: Long): Offset? {
        val n = nodeById[nodeId] ?: return null
        if (canvasW == 0f) return null
        val p = simPos[nodeId] ?: Offset(n.x, n.y)
        return Offset(canvasW / 2f + offset.x + p.x * scale, canvasH / 2f + offset.y + p.y * scale)
    }
    // One-tap "fit view": frame every visible node — the way back after getting lost panning
    // or zooming. 0.85 leaves a margin; the span guards keep a single node from dividing by ~0.
    fun fitView() {
        if (canvasW == 0f || snapshot.nodes.isEmpty()) return
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        snapshot.nodes.forEach { n ->
            val p = simPos[n.id] ?: Offset(n.x, n.y)
            if (p.x < minX) minX = p.x
            if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y
            if (p.y > maxY) maxY = p.y
        }
        val spanX = (maxX - minX).coerceAtLeast(40f)
        val spanY = (maxY - minY).coerceAtLeast(40f)
        scale = (0.85f * minOf(canvasW / spanX, canvasH / spanY)).coerceIn(0.3f, 3f)
        offset = Offset(-(minX + maxX) / 2f * scale, -(minY + maxY) / 2f * scale)
    }

    val overviewColor = Color(0xFF35C2B0) // teal — distinct from the warm-red accent
    // Overview lets you zoom in further (read live inside the gesture handler, which isn't re-keyed).
    val overviewModeState = rememberUpdatedState(overviewMode)

    val haptics = rememberHaptics()
    val primary = MaterialTheme.colorScheme.primary
    val orphanColor = MaterialTheme.colorScheme.error
    val nodeColor = MaterialTheme.colorScheme.onSurface
    val selectedColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelArgb = nodeColor.toArgb()
    // Reused native paint for node labels (cheap text draw; ≤30 active labels per frame).
    val labelPaint = remember {
        android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (snapshot.nodes.isEmpty()) {
                Text(
                    if (query.isBlank()) "Your graph is empty. Create notes — each becomes a node here."
                    else "No nodes match \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    // Unified gesture: drag a node to move it directly (its ≤3-hop neighbourhood
                    // reorients, the rest stay frozen); tap a node to select, double-tap or
                    // long-press it to open its note; drag/pinch empty space to pan/zoom (about
                    // the fingers); double-tap empty space to zoom in on that spot. One handler
                    // avoids detectors fighting over the same pointers.
                    .pointerInput(snapshot) {
                        // Double-tap bookkeeping lives across gestures; node id 0 marks "empty space"
                        // (Room ids start at 1).
                        var lastTapMs = 0L
                        var lastTapPos = Offset.Zero
                        var lastTapNode = -1L
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val hit = snapshot.nodes.firstOrNull { n ->
                                (screenOf(n) - down.position).getDistance() <= maxOf(screenRadiusOf(n), 28f)
                            }
                            // Zoom about a screen focal point — the spot under the fingers stays put.
                            fun zoomAbout(focal: Offset, factor: Float) {
                                val maxZoom = if (overviewModeState.value) 8f else 6f
                                val newScale = (scale * factor).coerceIn(0.3f, maxZoom)
                                if (newScale == scale) return
                                val f = focal - Offset(canvasW / 2f, canvasH / 2f)
                                offset = f - (f - offset) * (newScale / scale)
                                scale = newScale
                            }
                            fun isDoubleTap(of: Long) = lastTapNode == of &&
                                down.uptimeMillis - lastTapMs < DOUBLE_TAP_WINDOW_MS &&
                                (down.position - lastTapPos).getDistance() < 80f
                            fun recordTap(of: Long) {
                                lastTapMs = down.uptimeMillis; lastTapPos = down.position; lastTapNode = of
                            }
                            if (hit != null) {
                                // Hold still = long-press (open the note); move past slop = drag the
                                // node; release early = tap (select; a second tap opens the note).
                                val slop = viewConfiguration.touchSlop
                                var dragStart = down.position
                                var released = false
                                val waited = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                        if (!change.pressed) { released = true; break }
                                        if ((change.position - down.position).getDistance() > slop) {
                                            dragStart = change.position
                                            break
                                        }
                                    }
                                }
                                when {
                                    waited == null -> {
                                        // Long-press: straight into the note (concept nodes just select).
                                        haptics.strong()
                                        viewModel.select(hit.id)
                                        hit.noteId?.let { onOpenNote(it) }
                                        // Swallow the rest of the gesture so nothing else reacts to it.
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            event.changes.forEach { it.consume() }
                                            if (event.changes.none { it.pressed }) break
                                        }
                                    }
                                    released -> {
                                        // A tap = just select (NO reheat → no suction/motion);
                                        // a quick second tap on the same node opens its note.
                                        haptics.tick()
                                        viewModel.select(hit.id)
                                        if (isDoubleTap(hit.id)) hit.noteId?.let { onOpenNote(it) }
                                        recordTap(hit.id)
                                    }
                                    else -> {
                                        // Drag the node directly. Picking it up gets a heavier buzz.
                                        haptics.strong()
                                        viewModel.select(hit.id)
                                        layout.move(hit.id, (dragStart - down.position) / scale)
                                        var last = dragStart
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                            if (change == null || !change.pressed) break
                                            val delta = change.position - last
                                            last = change.position
                                            if (delta.getDistanceSquared() > 1f) {
                                                layout.move(hit.id, delta / scale) // screen → sim space (reheats)
                                                change.consume()
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Pan / zoom on empty space = viewport only. NO reheat — a static graph
                                // stays perfectly still while you navigate (this was the nav lag: every
                                // pan frame was waking the whole O(n²) simulation).
                                var moved = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    if (zoom != 1f) {
                                        // About the pinch centroid, so the pinched spot stays put.
                                        val centroid = event.calculateCentroid()
                                        zoomAbout(if (centroid.isSpecified) centroid else down.position, zoom)
                                    }
                                    if (pan != Offset.Zero) offset += pan
                                    if (zoom != 1f || pan != Offset.Zero) {
                                        moved = true
                                        event.changes.forEach { it.consume() }
                                    }
                                    if (event.changes.none { it.pressed }) break
                                }
                                if (!moved) {
                                    // Tap empty = deselect; double-tap empty = zoom in on that spot.
                                    if (isDoubleTap(0L)) zoomAbout(down.position, 1.6f)
                                    else viewModel.select(null)
                                    recordTap(0L)
                                }
                            }
                        }
                    },
            ) {
                canvasW = size.width
                canvasH = size.height

                fun active(id: Long) = activeSet == null || id in activeSet
                // Cheap viewport cull: skip anything well off-screen so a big panned graph only pays
                // for what's visible.
                val pad = 64f
                fun onScreen(p: Offset) = p.x >= -pad && p.x <= size.width + pad &&
                    p.y >= -pad && p.y <= size.height + pad

                // Edges first (under nodes). Greyed faint when not fully inside the active set.
                drawEdges.forEach { (e, a, b) ->
                    val pa = screenOf(a); val pb = screenOf(b)
                    if (!onScreen(pa) && !onScreen(pb)) return@forEach
                    val on = active(e.sourceId) && active(e.targetId)
                    val bothOverview = e.sourceId in overviewSet && e.targetId in overviewSet
                    val ga = groupColors[e.sourceId]; val gb = groupColors[e.targetId]
                    val edgeColor = when {
                        !on -> inactiveColor.copy(alpha = 0.10f)
                        bothOverview -> overviewColor.copy(alpha = 0.75f)
                        // AI-suggested connections pop in gold until confirmed (then they take
                        // the established colours below) — recent suggestions read at a glance.
                        e.aiGenerated -> SuggestedEdgeColor.copy(alpha = 0.9f)
                        // Link colour follows the group when both ends share it (Obsidian-style).
                        ga != null && ga == gb -> Color(ga).copy(alpha = 0.75f)
                        else -> primary.copy(alpha = 0.25f + 0.6f * e.strength)
                    }
                    drawLine(
                        color = edgeColor,
                        start = pa,
                        end = pb,
                        strokeWidth = if (e.aiGenerated) 2.2f else 1.8f,
                    )
                }

                // Nodes. Inactive ones are dim grey (and frozen); active ones get rings + a label.
                // Label size tracks zoom (smaller from further away) so a zoomed-out vault isn't
                // a wall of overlapping names; the user's size multiplier scales the whole range.
                labelPaint.textSize = (11f * scale * labelSizeMult).coerceIn(5f, 18f).sp.toPx()
                snapshot.nodes.forEach { n ->
                    val center = screenOf(n)
                    if (!onScreen(center)) return@forEach
                    val r = screenRadiusOf(n)
                    val on = active(n.id)
                    if (!on) {
                        drawCircle(color = inactiveColor.copy(alpha = 0.28f), radius = r, center = center)
                        return@forEach
                    }
                    val isOrphan = n.id !in degreeIds
                    val isSelected = n.id == selectedId
                    val inOverview = n.id in overviewSet
                    // Health mode paints memory strength (red = decaying → green = solid; grey =
                    // no cards yet). Otherwise: group colour wins, then overview teal, then default.
                    val health = if (healthMode) n.noteId?.let { noteHealth[it] } else null
                    val baseColor = when {
                        healthMode -> health?.let { healthColor(it.score) } ?: inactiveColor.copy(alpha = 0.45f)
                        groupColors[n.id] != null -> Color(groupColors[n.id]!!)
                        inOverview -> overviewColor
                        else -> nodeColor.copy(alpha = 0.9f)
                    }
                    drawCircle(color = baseColor, radius = r, center = center)
                    if (healthMode && (health?.due ?: 0) > 0) {
                        // Overdue cards → an urgent ring: this node needs revision NOW.
                        drawCircle(color = orphanColor, radius = r + 3f, center = center, style = Stroke(width = 2f))
                    } else if (isOrphan && !healthMode) {
                        drawCircle(color = orphanColor, radius = r + 3f, center = center, style = Stroke(width = 2f))
                    }
                    // Pinned → thick teal ring; other overview members → thin teal ring (outline only,
                    // group fill shows through); plain selection → accent ring.
                    when {
                        n.id in overviewPinned ->
                            drawCircle(color = overviewColor, radius = r + 6f, center = center, style = Stroke(width = 3f))
                        inOverview ->
                            drawCircle(color = overviewColor, radius = r + 4f, center = center, style = Stroke(width = 2f))
                        isSelected ->
                            drawCircle(color = selectedColor, radius = r + 6f, center = center, style = Stroke(width = 3f))
                    }
                    // Names appear only past the user's "render distance" zoom threshold.
                    if (scale >= labelMinScale && n.label.isNotBlank()) {
                        labelPaint.color = labelArgb
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                n.label.take(28),
                                center.x,
                                center.y + r + 14.dp.toPx(),
                                labelPaint,
                            )
                        }
                    }
                }
            }

            // AI summary boxes positioned below the relevant node(s). Normal mode: just the selected
            // node (clears on deselect). Overview mode: every pinned node (sticky). The placement
            // maths reads scale/offset/simPos every pan frame, so it lives in its own composable —
            // panning with boxes open recomposes just that layer, never this whole screen.
            val rawAnchors = if (overviewMode) overviewPinned.toList() else listOfNotNull(selectedId)
            val anchors = rawAnchors.filter { nodeById[it]?.noteId != null }
            SummaryBoxesLayer(
                anchors = anchors,
                nodeById = nodeById,
                allNodeIds = nodeIds,
                summaries = summaries,
                overviewMode = overviewMode,
                layout = overviewLayout,
                accent = overviewColor,
                nodeScreenPos = { id -> nodeScreenPos(id) },
                canvasSize = { Offset(canvasW, canvasH) },
                onClose = { nodeId -> if (overviewMode) viewModel.unpin(nodeId) else viewModel.select(null) },
            )

            // Search bar — "sequester" the graph to matching nodes/tags.
            GraphSearchBar(
                query = query,
                onQueryChange = viewModel::setQuery,
                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
            )

            // Edge tabs — page prev/next without the pager hijacking graph pans.
            EdgeTab(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous section", onSwipePrev, Modifier.align(Alignment.CenterStart))
            EdgeTab(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next section", onSwipeNext, Modifier.align(Alignment.CenterEnd))

            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.TopEnd).padding(20.dp),
                )
            }

            // ── Bottom control area — ONE tidy column: contextual Overview actions, the selected-
            // node card (or its restore pill), and the main tool pill. Replaces the old scattered
            // FAB stacks that covered the canvas corners.
            val selected = snapshot.nodes.firstOrNull { it.id == selectedId }
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Contextual Overview actions appear only when they can do something.
                if (overviewMode && (overviewPinned.isNotEmpty() || overviewDigest.isNotEmpty())) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (overviewPinned.isNotEmpty()) {
                            Button(onClick = { viewModel.sendOverviewToCompiler() }) {
                                Text("Send ${overviewPinned.size} → Compiler")
                            }
                        }
                        if (overviewLayout != OverviewLayout.LIST &&
                            overviewLayout != OverviewLayout.GRID &&
                            overviewDigest.isNotEmpty()
                        ) {
                            androidx.compose.material3.FilledTonalButton(onClick = { showAllOverviews = true }) {
                                Text("All overviews (${overviewDigest.size})")
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = selected != null && !optionsMinimized,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                ) {
                    // Keep the last non-null selection during the exit animation.
                    val shown = selected ?: remember { snapshot.nodes.firstOrNull { it.id == selectedId } }
                    if (shown != null) {
                        val incidentAi = snapshot.edges.filter {
                            it.aiGenerated && (it.sourceId == shown.id || it.targetId == shown.id)
                        }
                        SelectedNodeCard(
                            label = shown.label,
                            degree = snapshot.edges.count { it.sourceId == shown.id || it.targetId == shown.id },
                            suggestedCount = incidentAi.size,
                            hasNote = shown.noteId != null,
                            onOpen = { shown.noteId?.let { onOpenNote(it) } },
                            onSendToCompiler = viewModel::sendSelectedToCompiler,
                            onSuggest = viewModel::suggestConnectionsForSelected,
                            onConfirm = { incidentAi.forEach { viewModel.confirmEdge(it.id) } },
                            onLink = { showLinkPicker = true },
                            onDelete = viewModel::deleteSelectedNode,
                            onMinimize = { optionsMinimized = true },
                            onClose = { viewModel.select(null) },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }

                // When minimised but a node is still selected, a compact pill restores the card.
                AnimatedVisibility(visible = selected != null && optionsMinimized) {
                    androidx.compose.material3.FilledTonalButton(onClick = { optionsMinimized = false }) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Show options", modifier = Modifier.padding(start = 6.dp))
                    }
                }

                // The main tool pill — hidden while the full selected-node card is up.
                AnimatedVisibility(visible = selected == null || optionsMinimized) {
                    GraphToolbar(
                        overviewMode = overviewMode,
                        healthMode = healthMode,
                        holistic = holistic,
                        allCount = fullSnapshot.nodes.size,
                        onToggleOverview = viewModel::toggleOverviewMode,
                        onToggleHealth = viewModel::toggleHealthMode,
                        onToggleHolistic = viewModel::toggleHolistic,
                        onFit = { fitView() },
                        onReload = viewModel::loadFullGraph,
                        onAdd = { showAddNode = true },
                        onGroups = { showGroups = true },
                        onCompiler = onOpenCompiler,
                        onSettings = { showSettings = true },
                    )
                }
            }

            // First-open focus chooser: pick a group/tag to focus on, or "Everything". The choice
            // is remembered, so next time the graph opens straight to it (re-openable in settings).
            if (!focusChosen) {
                FocusChooserOverlay(
                    groups = groups,
                    onEverything = { viewModel.setFocus("") },
                    onPick = { tag -> viewModel.setFocus(tag) },
                )
            }
        }
    }

    if (showLinkPicker) {
        val others = fullSnapshot.nodes.filter { it.id != selectedId }
        LinkPickerDialog(
            nodes = others,
            onPick = { id -> viewModel.linkSelectedTo(id); showLinkPicker = false },
            onDismiss = { showLinkPicker = false },
        )
    }

    if (showAddNode) {
        AddNodeDialog(
            onAdd = { label -> viewModel.addNode(label); showAddNode = false },
            onDismiss = { showAddNode = false },
        )
    }

    if (showSettings) {
        GraphSettingsDialog(
            labelMinScale = labelMinScale,
            onLabelMinScale = viewModel::setLabelMinScale,
            labelSizeMult = labelSizeMult,
            onLabelSizeMult = viewModel::setLabelSizeMult,
            settleTemp = settleTemp,
            onSettleTemp = viewModel::setSettleTemp,
            layout = overviewLayout,
            onLayout = viewModel::setOverviewLayout,
            groups = groups,
            focusTag = focusTag,
            onSetFocus = viewModel::setFocus,
            openZoomRecent = openZoomRecent,
            onOpenZoomRecent = viewModel::setOpenZoomRecent,
            onReopenChooser = { viewModel.reopenFocusChooser(); showSettings = false },
            onDismiss = { showSettings = false },
        )
    }

    if (showGroups) {
        GraphGroupsDialog(
            groups = groups,
            tagSuggestions = allTags,
            presets = groupPresets,
            activePreset = activePreset,
            onSelectPreset = viewModel::selectPreset,
            onAutoAssign = viewModel::autoAssignGroups,
            onAdd = { tag, color -> viewModel.addGroup(tag, color) },
            onRemove = viewModel::removeGroup,
            onMove = { tag, up -> viewModel.moveGroup(tag, up) },
            onDismiss = { showGroups = false },
        )
    }

    if (showAllOverviews) {
        AllOverviewsDialog(
            entries = overviewDigest,
            onOpen = { entry ->
                fullSnapshot.nodes.firstOrNull { it.noteId == entry.noteId }?.let { viewModel.select(it.id) }
                showAllOverviews = false
            },
            onDismiss = { showAllOverviews = false },
        )
    }
}

/** A scrollable digest of every AI overview generated this session — the "All overviews" panel. */
@Composable
private fun AllOverviewsDialog(
    entries: List<OverviewEntry>,
    onOpen: (OverviewEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("All overviews (${entries.size})") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                entries.forEach { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(entry) },
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                entry.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                entry.summary,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        },
    )
}

/**
 * The summary boxes, arranged per the user's Overview layout setting: TILED near their nodes
 * (push-apart), a RADIAL ring around the cluster, a LIST line to the right of it, or a screen-
 * pinned GRID along the bottom. Tiled/radial boxes get a connector line back to their node.
 * Isolated in its own composable ON PURPOSE: [nodeScreenPos] reads the pan/zoom transform and the
 * live sim positions, so while panning with boxes open only THIS layer recomposes each frame —
 * the rest of the graph screen (canvas, controls, cards) stays untouched.
 */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.SummaryBoxesLayer(
    anchors: List<Long>,
    nodeById: Map<Long, GraphNode>,
    allNodeIds: List<Long>,
    summaries: Map<Long, String>,
    overviewMode: Boolean,
    layout: OverviewLayout,
    accent: Color,
    nodeScreenPos: (Long) -> Offset?,
    canvasSize: () -> Offset,
    onClose: (Long) -> Unit,
) {
    if (anchors.isEmpty()) return
    val density = LocalDensity.current
    val boxWpx = with(density) { 240.dp.toPx() }
    val boxHpx = with(density) { 156.dp.toPx() }
    val gapPx = with(density) { 80.dp.toPx() } // gap between a node and its tiled box
    val smallGap = with(density) { 14.dp.toPx() }
    // A single selected node's box (outside Overview) always tiles next to its node.
    val effective = if (overviewMode) layout else OverviewLayout.TILED
    val boxPlacements: Map<Long, Offset> = when (effective) {
        // A horizontal line of synopses to the RIGHT of the cluster. Anchored to the graph (uses
        // node screen positions) so it pans/zooms WITH the graph.
        OverviewLayout.LIST -> {
            val pts = allNodeIds.mapNotNull { nodeScreenPos(it) }
            if (pts.isEmpty()) emptyMap()
            else {
                val startX = pts.maxOf { it.x } + with(density) { 40.dp.toPx() }
                val cy = pts.map { it.y }.average().toFloat()
                val stepX = boxWpx + smallGap
                anchors.mapIndexed { idx, id ->
                    id to Offset(startX + idx * stepX, cy - boxHpx / 2f)
                }.toMap()
            }
        }
        // Screen-pinned rows along the bottom (newest row grows upward) — a reading panel that
        // stays put while the graph pans behind it.
        OverviewLayout.GRID -> {
            val size = canvasSize()
            if (size.x <= 0f) emptyMap()
            else {
                val cols = kotlin.math.max(1, ((size.x - smallGap) / (boxWpx + smallGap)).toInt())
                val totalW = cols * boxWpx + (cols - 1) * smallGap
                val startX = (size.x - totalW) / 2f
                val bottomPad = with(density) { 24.dp.toPx() }
                anchors.mapIndexed { i, id ->
                    val col = i % cols
                    val row = i / cols
                    id to Offset(
                        startX + col * (boxWpx + smallGap),
                        size.y - bottomPad - (row + 1) * (boxHpx + smallGap),
                    )
                }.toMap()
            }
        }
        // A ring around the cluster: each box sits just outside the cluster's edge, at its own
        // node's angle from the centre — links read outward like spokes.
        OverviewLayout.RADIAL -> {
            val pts = allNodeIds.mapNotNull { nodeScreenPos(it) }
            if (pts.isEmpty()) emptyMap()
            else {
                val cx = pts.map { it.x }.average().toFloat()
                val cy = pts.map { it.y }.average().toFloat()
                val clusterR = pts.maxOf { hypot((it.x - cx).toDouble(), (it.y - cy).toDouble()).toFloat() }
                val ring = clusterR + gapPx + boxHpx / 2f
                val desired = anchors.mapNotNull { id ->
                    val p = nodeScreenPos(id) ?: return@mapNotNull null
                    val dx = p.x - cx
                    val dy = p.y - cy
                    val d = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                    val ux = if (d < 1f) 1f else dx / d
                    val uy = if (d < 1f) 0f else dy / d
                    id to Offset(cx + ux * ring - boxWpx / 2f, cy + uy * ring - boxHpx / 2f)
                }
                separateBoxes(desired, boxWpx, boxHpx).toMap()
            }
        }
        // Each box sits near its node, pushed apart so none overlap.
        OverviewLayout.TILED -> {
            val desired = anchors.mapNotNull { id ->
                val p = nodeScreenPos(id) ?: return@mapNotNull null
                id to Offset(p.x - boxWpx / 2f, p.y + gapPx)
            }
            separateBoxes(desired, boxWpx, boxHpx).toMap()
        }
    }
    // Connector lines from each node to its box, so a spread-out layout still reads unambiguously.
    // Skipped for the panel-like layouts (list/grid), where connectors would criss-cross the screen.
    if (effective == OverviewLayout.TILED || effective == OverviewLayout.RADIAL) {
        Canvas(Modifier.matchParentSize()) {
            anchors.forEach inner@{ id ->
                val from = nodeScreenPos(id) ?: return@inner
                val to = boxPlacements[id] ?: return@inner
                drawLine(
                    color = accent.copy(alpha = 0.45f),
                    start = from,
                    end = Offset(to.x + boxWpx / 2f, to.y),
                    strokeWidth = 2f,
                )
            }
        }
    }
    anchors.forEach { nodeId ->
        val node = nodeById[nodeId] ?: return@forEach
        val noteId = node.noteId ?: return@forEach
        val topLeft = boxPlacements[nodeId] ?: return@forEach
        androidx.compose.runtime.key(nodeId) {
            SummaryBox(
                title = node.label,
                text = summaries[noteId],
                overview = overviewMode,
                accent = accent,
                onClose = { onClose(nodeId) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()) },
            )
        }
    }
}

/**
 * Visually separate a set of summary boxes so they never overlap. Pure layout maths on screen
 * rectangles (top-left + fixed size) — it does NOT touch the graph physics; it just nudges boxes
 * apart along their least-overlap axis. Returns the adjusted top-lefts in the input order.
 */
private fun separateBoxes(items: List<Pair<Long, Offset>>, w: Float, h: Float): List<Pair<Long, Offset>> {
    if (items.size <= 1) return items
    val n = items.size
    val xs = FloatArray(n) { items[it].second.x }
    val ys = FloatArray(n) { items[it].second.y }
    repeat(14) {
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val dx = (xs[i] + w / 2f) - (xs[j] + w / 2f)
                val dy = (ys[i] + h / 2f) - (ys[j] + h / 2f)
                val ox = w + 8f - kotlin.math.abs(dx) // +8 for a small gutter between boxes
                val oy = h + 8f - kotlin.math.abs(dy)
                if (ox > 0f && oy > 0f) {
                    if (ox < oy) {
                        val push = ox / 2f * (if (dx < 0f) -1f else 1f)
                        xs[i] += push; xs[j] -= push
                    } else {
                        val push = oy / 2f * (if (dy < 0f) -1f else 1f)
                        ys[i] += push; ys[j] -= push
                    }
                }
            }
        }
    }
    return items.mapIndexed { idx, (id, _) -> id to Offset(xs[idx], ys[idx]) }
}

/** Full-screen chooser shown on first graph open: focus a group/tag, or show everything. */
@Composable
private fun FocusChooserOverlay(
    groups: List<com.generic.mnemos.core.settings.GraphGroup>,
    onEverything: () -> Unit,
    onPick: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
    ) {
        Column(
            Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Focus the graph", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Open straight to a colour group, or see everything. You can change this any time in graph settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onEverything, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.BubbleChart, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Everything", modifier = Modifier.padding(start = 8.dp))
            }
            if (groups.isEmpty()) {
                Text(
                    "No colour groups yet — add some with the Groups button to focus by tag.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Focus a group", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                groups.forEach { g ->
                    OutlinedButton(onClick = { onPick(g.tag) }, modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.size(16.dp).clip(CircleShape).background(Color(g.colorArgb)))
                        Text("#${g.tag}", modifier = Modifier.padding(start = 10.dp).weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * AI summary box shown for a node on the graph — headed by the note's title. EVERY box is the
 * same fixed 240×156dp: the size never tracks the screen, the zoom, or the summary's length
 * (long text scrolls inside instead of growing the box). The fixed size also matches the
 * placement maths in [SummaryBoxesLayer] exactly, so boxes never overlap or drift.
 */
@Composable
private fun SummaryBox(
    title: String,
    text: String?,
    overview: Boolean,
    accent: Color,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(240.dp).height(156.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        border = if (overview) androidx.compose.foundation.BorderStroke(2.dp, accent) else null,
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    title.ifBlank { if (overview) "Overview summary" else "Summary" },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (overview) accent else MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(20.dp)) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Close summary",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (text == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    Text("Summarising…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // Long summaries scroll within the fixed box (no Extend button, no size change).
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

/** Graph settings: name render distance + Overview synopsis layout + focus-on-open. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GraphSettingsDialog(
    labelMinScale: Float,
    onLabelMinScale: (Float) -> Unit,
    labelSizeMult: Float,
    onLabelSizeMult: (Float) -> Unit,
    settleTemp: Float,
    onSettleTemp: (Float) -> Unit,
    layout: OverviewLayout,
    onLayout: (OverviewLayout) -> Unit,
    groups: List<com.generic.mnemos.core.settings.GraphGroup>,
    focusTag: String,
    onSetFocus: (String) -> Unit,
    openZoomRecent: Boolean,
    onOpenZoomRecent: (Boolean) -> Unit,
    onReopenChooser: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Graph settings") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text("Name render distance", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Lower = names show even when zoomed out; higher = only when zoomed in close.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Slider runs HIGH→LOW scale so dragging right = "see names from further out".
                androidx.compose.material3.Slider(
                    value = 3.2f - labelMinScale,
                    onValueChange = { onLabelMinScale((3.2f - it).coerceIn(0.2f, 3f)) },
                    valueRange = 0.2f..3f,
                )

                androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Name size", style = MaterialTheme.typography.labelLarge)
                Text(
                    "How large node names draw (they also shrink as you zoom out).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.Slider(
                    value = labelSizeMult,
                    onValueChange = { onLabelSizeMult(it) },
                    valueRange = 0.6f..1.6f,
                )

                androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Opening motion", style = MaterialTheme.typography.labelLarge)
                Text(
                    "How alive the graph is when it opens: left = perfectly still, right = a longer settle animation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.material3.Slider(
                    value = settleTemp,
                    onValueChange = { onSettleTemp(it) },
                    valueRange = 0f..1f,
                )

                androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Overview synopsis layout", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Tiled = boxes by their nodes · Radial = a ring around the cluster · " +
                        "List = a row beside the graph · Grid = a fixed panel at the bottom.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OverviewLayout.entries.forEach { opt ->
                        androidx.compose.material3.FilterChip(
                            selected = layout == opt,
                            onClick = { onLayout(opt) },
                            label = {
                                Text(
                                    when (opt) {
                                        OverviewLayout.TILED -> "Tiled"
                                        OverviewLayout.RADIAL -> "Radial"
                                        OverviewLayout.LIST -> "List"
                                        OverviewLayout.GRID -> "Grid"
                                    },
                                )
                            },
                        )
                    }
                }

                androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Focus on open", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Which slice of the graph to show when you open it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.FilterChip(
                        selected = focusTag.isBlank(),
                        onClick = { onSetFocus("") },
                        label = { Text("Everything") },
                    )
                    groups.forEach { g ->
                        androidx.compose.material3.FilterChip(
                            selected = focusTag.equals(g.tag, true),
                            onClick = { onSetFocus(g.tag) },
                            label = { Text("#${g.tag}") },
                        )
                    }
                }
                TextButton(onClick = onReopenChooser) { Text("Show chooser on next open") }

                androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Open on recent note", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Zoom in on & select the last note you focused.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Switch(checked = openZoomRecent, onCheckedChange = onOpenZoomRecent)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

private val GROUP_PALETTE = listOf(
    0xFFE0A12E.toInt(), 0xFF49B675.toInt(), 0xFF4F9DDE.toInt(),
    0xFFB05CD6.toInt(), 0xFFD9534F.toInt(), 0xFF5BC0C7.toInt(), 0xFFEA7CC0.toInt(),
)

/** Graph colour groups: a node whose note has tag X renders in the colour you assign to X. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GraphGroupsDialog(
    groups: List<com.generic.mnemos.core.settings.GraphGroup>,
    tagSuggestions: List<String>,
    presets: List<List<com.generic.mnemos.core.settings.GraphGroup>>,
    activePreset: Int,
    onSelectPreset: (Int) -> Unit,
    onAutoAssign: () -> Unit,
    onAdd: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var tag by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(GROUP_PALETTE.first()) }
    // Existing tags (most-frequent first) that aren't already a group — quick-pick suggestions.
    val suggestions = tagSuggestions.filter { s -> groups.none { it.tag.equals(s, true) } }.take(12)
    val presetCount = com.generic.mnemos.core.settings.GRAPH_GROUP_PRESET_COUNT
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colour groups") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                // Preset selector — 5 full-graph colourings; the active one is what you're editing.
                Text("Preset", style = MaterialTheme.typography.labelLarge)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(presetCount) { i ->
                        val count = presets.getOrNull(i)?.size ?: 0
                        androidx.compose.material3.FilterChip(
                            selected = i == activePreset,
                            onClick = { onSelectPreset(i) },
                            label = { Text("P${i + 1}${if (count > 0) " ($count)" else ""}") },
                        )
                    }
                }
                OutlinedButton(onClick = onAutoAssign, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Auto-colour top 8 by connections", modifier = Modifier.padding(start = 8.dp))
                }
                androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))

                if (groups.isEmpty()) {
                    Text(
                        "Add a tag and pick a colour — every note with that tag colours its node.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Order = priority: the topmost matching group colours a node. Use the arrows to reorder.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                groups.forEachIndexed { index, g ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).background(Color(g.colorArgb)))
                        Text("#${g.tag}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { onMove(g.tag, true) },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move up")
                        }
                        IconButton(
                            onClick = { onMove(g.tag, false) },
                            enabled = index < groups.lastIndex,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move down")
                        }
                        TextButton(onClick = { onRemove(g.tag) }) { Text("Remove") }
                    }
                }
                androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("Tag (e.g. biology)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (suggestions.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        suggestions.forEach { s ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { tag = s },
                            ) {
                                Text(
                                    "#$s",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                )
                            }
                        }
                    }
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GROUP_PALETTE.forEach { c ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(c))
                                .clickable { color = c }
                                .then(
                                    if (c == color) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp))
                                    else Modifier,
                                ),
                        )
                    }
                }
                // Custom colour via a gradient picker — not limited to the swatches above.
                ColorGradientPicker(selected = color, onPick = { color = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(tag, color); tag = "" }, enabled = tag.isNotBlank()) { Text("Add group") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private val HUE_STOPS = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
    Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000),
)

/** A gradient colour picker: drag the hue bar + brightness slider to choose any colour. */
@Composable
private fun ColorGradientPicker(selected: Int, onPick: (Int) -> Unit) {
    var hue by remember { mutableStateOf(0f) }
    var value by remember { mutableStateOf(0.9f) }
    var barWidth by remember { mutableStateOf(1f) }
    fun emit() { onPick(Color.hsv(hue, 0.7f, value).toArgb()) }
    Column(Modifier.padding(top = 12.dp)) {
        Text("Custom colour", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Brush.horizontalGradient(HUE_STOPS))
                .onSizeChanged { barWidth = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    detectTapGestures { off -> hue = (off.x / barWidth * 360f).coerceIn(0f, 360f); emit() }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        hue = (change.position.x / barWidth * 360f).coerceIn(0f, 360f); emit()
                    }
                },
        )
        androidx.compose.material3.Slider(
            value = value,
            onValueChange = { value = it; emit() },
            valueRange = 0.3f..1f,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(6.dp)).background(Color(selected)))
            Text(
                "Tap or drag the bar to pick any colour.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GraphSearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search nodes, tags…") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                    }
                }
            },
        )
    }
}

/** Memory-strength colour ramp for health mode: red (decaying) → amber → green (solid). */
private fun healthColor(score: Float): Color {
    val red = Color(0xFFD9534F)
    val amber = Color(0xFFE0A12E)
    val green = Color(0xFF49B675)
    return if (score < 0.5f) androidx.compose.ui.graphics.lerp(red, amber, (score * 2f).coerceIn(0f, 1f))
    else androidx.compose.ui.graphics.lerp(amber, green, ((score - 0.5f) * 2f).coerceIn(0f, 1f))
}

/**
 * The graph's single control surface: a floating pill of NAMED tools at the bottom of the screen
 * — every button says what it does (no icon-guessing). Mode buttons (Overview / Health / Show-all)
 * are TOGGLES with a lit ON state; actions (load, add node, groups, compiler, settings) follow the
 * divider. The row scrolls horizontally when the labels don't all fit.
 */
@Composable
private fun GraphToolbar(
    overviewMode: Boolean,
    healthMode: Boolean,
    holistic: Boolean,
    allCount: Int,
    onToggleOverview: () -> Unit,
    onToggleHealth: () -> Unit,
    onToggleHolistic: () -> Unit,
    onFit: () -> Unit,
    onReload: () -> Unit,
    onAdd: () -> Unit,
    onGroups: () -> Unit,
    onCompiler: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 5.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BarToggle(Icons.Outlined.Layers, "Overview", checked = overviewMode, onClick = onToggleOverview)
            BarToggle(Icons.Outlined.MonitorHeart, "Health", checked = healthMode, onClick = onToggleHealth)
            BarToggle(
                if (holistic) Icons.Outlined.FilterCenterFocus else Icons.Outlined.BubbleChart,
                if (holistic) "Focus view" else "Show all ($allCount)",
                checked = holistic,
                onClick = onToggleHolistic,
            )
            androidx.compose.material3.VerticalDivider(Modifier.height(24.dp))
            BarButton(Icons.Outlined.FitScreen, "Fit view", onFit)
            BarButton(Icons.Outlined.Refresh, "Load all nodes", onReload)
            BarButton(Icons.Outlined.Add, "Add node", onAdd)
            BarButton(Icons.Outlined.Category, "Groups", onGroups)
            BarButton(Icons.Outlined.Dashboard, "Compiler", onCompiler)
            BarButton(Icons.Outlined.Settings, "Settings", onSettings)
        }
    }
}

/** A named toolbar action: small icon + its label, in a tappable pill. */
@Composable
private fun BarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** A named toolbar toggle: lit in the accent colour while ON. */
@Composable
private fun BarToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

/** Create a standalone concept node by name. */
@Composable
private fun AddNodeDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add node") },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Concept name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(label) }, enabled = label.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A slim tab pinned to a screen edge that flicks to the neighbouring section. */
@Composable
private fun EdgeTab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    cd: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(vertical = 2.dp)
            .size(width = 24.dp, height = 84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = cd, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SelectedNodeCard(
    label: String,
    degree: Int,
    suggestedCount: Int,
    hasNote: Boolean,
    onOpen: () -> Unit,
    onSendToCompiler: () -> Unit,
    onSuggest: () -> Unit,
    onConfirm: () -> Unit,
    onLink: () -> Unit,
    onDelete: () -> Unit,
    onMinimize: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onMinimize) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Minimise options")
                }
            }
            Text(
                "$degree connection${if (degree == 1) "" else "s"}" +
                    if (suggestedCount > 0) " · $suggestedCount suggested" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (hasNote) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onOpen) { Text("Open note") }
                    OutlinedButton(onClick = onSendToCompiler) { Text("→ Compiler") }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onLink) { Text("Link") }
                OutlinedButton(onClick = onSuggest) { Text("Suggest (AI)") }
                if (suggestedCount > 0) {
                    OutlinedButton(onClick = onConfirm) { Text("Confirm") }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onClose) { Text("Close") }
                OutlinedButton(onClick = onDelete) {
                    Text("Delete node", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun LinkPickerDialog(nodes: List<GraphNode>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link to…") },
        text = {
            if (nodes.isEmpty()) {
                Text("No other nodes to link to yet.")
            } else {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    nodes.forEach { n ->
                        Text(
                            n.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(n.id) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
