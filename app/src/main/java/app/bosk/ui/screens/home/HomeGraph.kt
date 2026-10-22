package app.bosk.ui.screens.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import app.bosk.core.graph.GraphSnapshot
import app.bosk.ui.screens.graph.rememberForceLayout
import kotlin.math.cos
import kotlin.math.sin

/**
 * The home "live graph" — the current note's node plus its one-hop neighbours, laid out with
 * the same elastic force simulation as the full Graph tab and auto-fitted into the small panel.
 * The current note pulses; links it authored show as connections.
 */
@Composable
fun HomeGraph(snapshot: GraphSnapshot, currentNoteId: Long?, modifier: Modifier = Modifier) {
    val sub = remember(snapshot, currentNoteId) { subgraph(snapshot, currentNoteId) }

    if (sub.nodeIds.size < 1) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "Link notes with [[name]] to grow your graph",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    // Only simulate/animate while Home is the visible page — an off-screen neighbour shouldn't burn
    // frames behind a swipe.
    val pageActive = app.bosk.ui.navigation.LocalPageActive.current

    val layout = rememberForceLayout(
        nodeIds = sub.nodeIds,
        edges = sub.edges,
        running = pageActive,
        seed = { id ->
            if (id == sub.centerId) Offset.Zero
            else {
                val i = sub.nodeIds.indexOf(id).coerceAtLeast(0)
                val a = i * 2.399963f // golden-angle scatter
                Offset(cos(a) * 70f, sin(a) * 70f)
            }
        },
    )
    val pos = layout.positions

    // Only CREATE the pulse animation while Home is the visible page AND there's a centre node
    // to pulse — an infinite transition keeps the frame clock busy even when nothing reads it,
    // so otherwise it must be disposed entirely, not just ignored. The value is read in the
    // draw phase (below), never here, so the pulse redraws only the canvas.
    val pulse: androidx.compose.runtime.State<Float>? = if (pageActive && sub.centerId != null) {
        rememberInfiniteTransition(label = "homeGraphPulse").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Restart),
            label = "pulse",
        )
    } else null

    val white = Color.White
    // The note currently being previewed in "Now Reading" is highlighted in orange so it stands
    // out from the red accent of ordinary nodes.
    val highlight = Color(0xFFFF7A1A)

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { layout.reheat() } },
    ) {
        // This block re-runs every animation frame while the pulse is live — read positions
        // straight from the snapshot map (no per-frame list/pair/map allocations).
        val pulseF = pulse?.value ?: 0f
        if (sub.nodeIds.isEmpty()) return@Canvas

        // Auto-fit graph-space → canvas with padding.
        val pad = 16f
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        sub.nodeIds.forEach { id ->
            val p = pos[id] ?: Offset.Zero
            if (p.x < minX) minX = p.x; if (p.x > maxX) maxX = p.x
            if (p.y < minY) minY = p.y; if (p.y > maxY) maxY = p.y
        }
        val spanX = (maxX - minX).coerceAtLeast(1f)
        val spanY = (maxY - minY).coerceAtLeast(1f)
        val s = minOf((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        fun screen(p: Offset) = Offset(
            size.width / 2f + (p.x - cx) * s,
            size.height / 2f + (p.y - cy) * s,
        )

        sub.edges.forEach { (a, b) ->
            val pa = pos[a] ?: Offset.Zero
            val pb = pos[b] ?: Offset.Zero
            drawLine(white.copy(alpha = 0.30f), screen(pa), screen(pb), strokeWidth = 1.4f)
        }
        sub.nodeIds.forEach { id ->
            val c = screen(pos[id] ?: Offset.Zero)
            if (id == sub.centerId) {
                // Pulsing orange halo + solid orange dot for the previewed note.
                drawCircle(highlight.copy(alpha = (1f - pulseF) * 0.45f), radius = 7f + pulseF * 20f, center = c)
                drawCircle(highlight, radius = 7f, center = c)
            } else {
                drawCircle(white.copy(alpha = 0.5f), radius = 4.5f, center = c)
            }
        }
    }
}

private data class Sub(val nodeIds: List<Long>, val edges: List<Pair<Long, Long>>, val centerId: Long?)

private fun subgraph(snapshot: GraphSnapshot, currentNoteId: Long?): Sub {
    if (snapshot.nodes.isEmpty()) return Sub(emptyList(), emptyList(), null)
    val center = currentNoteId?.let { id -> snapshot.nodes.firstOrNull { it.noteId == id } }
    // No current-note node yet → show the whole (small) graph so it isn't blank.
    if (center == null) {
        val ids = snapshot.nodes.map { it.id }.take(12)
        val idSet = ids.toHashSet()
        val edges = snapshot.edges.filter { it.sourceId in idSet && it.targetId in idSet }
            .map { it.sourceId to it.targetId }
        return Sub(ids, edges, null)
    }
    val neighbourIds = HashSet<Long>().apply { add(center.id) }
    snapshot.edges.forEach { e ->
        if (e.sourceId == center.id) neighbourIds.add(e.targetId)
        if (e.targetId == center.id) neighbourIds.add(e.sourceId)
    }
    val ids = neighbourIds.toList()
    val edges = snapshot.edges
        .filter { it.sourceId in neighbourIds && it.targetId in neighbourIds }
        .map { it.sourceId to it.targetId }
    return Sub(ids, edges, center.id)
}
