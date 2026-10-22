package app.bosk.ui.screens.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bosk.core.graph.GraphNode
import kotlin.math.sqrt

private const val AI_MIN_NODE_R = 2f
private const val AI_MAX_NODE_R = 16f

/** A small labelled action pill under the AI-graph prompt. */
@Composable
private fun AiGraphAction(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

/** A small labelled toggle pill — lit in the accent colour while ON. */
@Composable
private fun AiGraphToggle(label: String, checked: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

/**
 * The "AI graph" sub-tab: an AI-assisted graph MAKER. Prompt-first — describe a topic, the AI
 * finds the relevant notes, and a fresh scoped graph of those nodes (+ neighbours) is drawn.
 * Embedded in [GraphSection] (no Scaffold of its own). Pan / zoom / drag / select as usual;
 * tapping a node offers to open the note.
 */
@Composable
fun AiGraphScreen(
    onOpenNote: (Long) -> Unit = {},
    onOpenTutor: () -> Unit = {},
    viewModel: AiGraphViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val matchedIds by viewModel.matchedNodeIds.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedNodeId.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val lastQuery by viewModel.lastQuery.collectAsStateWithLifecycle()
    val matchedOnly by viewModel.matchedOnly.collectAsStateWithLifecycle()

    var prompt by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) { message?.let { status = it; viewModel.consumeMessage() } }
    val send = { if (prompt.isNotBlank() && !busy) { viewModel.build(prompt); prompt = "" } }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var canvasW by remember { mutableFloatStateOf(0f) }
    var canvasH by remember { mutableFloatStateOf(0f) }

    val nodeById = remember(snapshot) { snapshot.nodes.associateBy { it.id } }
    val layout = rememberForceLayout(
        nodeIds = remember(snapshot) { snapshot.nodes.map { it.id } },
        edges = remember(snapshot) { snapshot.edges.map { it.sourceId to it.targetId } },
        settleOnSeed = true,
        seed = { id -> nodeById[id]?.let { Offset(it.x, it.y) } ?: Offset.Zero },
    )
    val simPos = layout.positions

    fun radiusOf(n: GraphNode) = 6f + sqrt(n.size) * 3f
    fun screenRadiusOf(n: GraphNode) = (radiusOf(n) * scale).coerceIn(AI_MIN_NODE_R, AI_MAX_NODE_R)
    fun screenOf(n: GraphNode): Offset {
        val p = simPos[n.id] ?: Offset(n.x, n.y)
        return Offset(canvasW / 2f + offset.x + p.x * scale, canvasH / 2f + offset.y + p.y * scale)
    }

    val labelPaint = remember {
        android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER }
    }

    val accent = MaterialTheme.colorScheme.primary
    val matchedColor = MaterialTheme.colorScheme.tertiary
    val neighbourColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val labelArgb = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    Column(Modifier.fillMaxSize()) {
        // Prompt bar — the maker's front door, always visible at the top.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Describe a topic — I'll graph the related notes") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { send() }),
                trailingIcon = {
                    if (busy) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else {
                        IconButton(onClick = send) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Build graph")
                        }
                    }
                },
            )
        }
        // Current graph's title + clear, plus the latest status line.
        if (lastQuery != null || status != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    lastQuery?.let { "“$it”" } ?: status.orEmpty(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (lastQuery != null) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (lastQuery != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { viewModel.clear(); status = null },
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Clear",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
            }
        }

        // Result actions — a built graph becomes a workflow: study it, card it, discuss it.
        // "Matches only" (ON by default) hides the pulled-in neighbours so the graph is exactly
        // what the prompt found; switch it off to see the surrounding context.
        if (lastQuery != null && snapshot.nodes.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AiGraphToggle("Matches only", checked = matchedOnly) { viewModel.toggleMatchedOnly() }
                AiGraphAction("→ Compiler") { viewModel.sendToCompiler() }
                AiGraphAction(if (busy) "Making cards…" else "Make cards") { if (!busy) viewModel.makeCards() }
                AiGraphAction("Tutor") { viewModel.sendToCompiler(); onOpenTutor() }
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (snapshot.nodes.isEmpty()) {
                Text(
                    if (busy) "Scanning your vault…"
                    else "Ask for a topic above — the AI finds the relevant notes and draws them as a fresh graph.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(snapshot) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val hit = snapshot.nodes.firstOrNull { n ->
                                (screenOf(n) - down.position).getDistance() <= maxOf(screenRadiusOf(n), 28f)
                            }
                            if (hit != null) {
                                var last = down.position
                                var moved = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed) break
                                    val delta = change.position - last
                                    last = change.position
                                    if (delta.getDistanceSquared() > 1f) {
                                        if (!moved) { moved = true; viewModel.select(hit.id) }
                                        layout.move(hit.id, delta / scale)
                                        change.consume()
                                    }
                                }
                                if (!moved) viewModel.select(hit.id)
                            } else {
                                var moved = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    if (zoom != 1f) scale = (scale * zoom).coerceIn(0.3f, 6f)
                                    if (pan != Offset.Zero) offset += pan
                                    if (zoom != 1f || pan != Offset.Zero) {
                                        moved = true
                                        event.changes.forEach { it.consume() }
                                    }
                                    if (event.changes.none { it.pressed }) break
                                }
                                if (!moved) viewModel.select(null)
                            }
                        }
                    },
            ) {
                canvasW = size.width
                canvasH = size.height
                val pad = 64f
                fun onScreen(p: Offset) = p.x >= -pad && p.x <= size.width + pad &&
                    p.y >= -pad && p.y <= size.height + pad

                snapshot.edges.forEach { e ->
                    val a = nodeById[e.sourceId] ?: return@forEach
                    val b = nodeById[e.targetId] ?: return@forEach
                    val pa = screenOf(a); val pb = screenOf(b)
                    if (!onScreen(pa) && !onScreen(pb)) return@forEach
                    val bothMatched = e.sourceId in matchedIds && e.targetId in matchedIds
                    drawLine(
                        color = (if (bothMatched) matchedColor else neighbourColor)
                            .copy(alpha = 0.2f + 0.5f * e.strength),
                        start = pa,
                        end = pb,
                        strokeWidth = if (e.aiGenerated) 1.2f else 1.8f,
                    )
                }

                // Label size tracks zoom so a zoomed-out view isn't crowded with names.
                labelPaint.textSize = (11f * scale).coerceIn(6f, 13f).sp.toPx()
                snapshot.nodes.forEach { n ->
                    val center = screenOf(n)
                    if (!onScreen(center)) return@forEach
                    val r = screenRadiusOf(n)
                    val isMatched = n.id in matchedIds
                    val isSelected = n.id == selectedId
                    drawCircle(
                        color = if (isMatched) matchedColor else neighbourColor,
                        radius = r,
                        center = center,
                    )
                    if (isSelected) {
                        drawCircle(color = accent, radius = r + 4f, center = center, style = Stroke(width = 2f))
                    }
                    // Names only close-up (matches the vault graph's default render distance).
                    if (scale >= app.bosk.core.settings.DEFAULT_LABEL_MIN_SCALE && n.label.isNotBlank()) {
                        labelPaint.color = labelArgb
                        drawContext.canvas.nativeCanvas.drawText(
                            n.label.take(24),
                            center.x,
                            center.y + r + 13.sp.toPx(),
                            labelPaint,
                        )
                    }
                }
            }

            // Selected-node card (open the underlying note).
            val selected = snapshot.nodes.firstOrNull { it.id == selectedId }
            if (selected != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                selected.label.ifBlank { "Untitled" },
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                            )
                            Text(
                                if (selected.id in matchedIds) "Matched your prompt" else "Connected neighbour",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        selected.noteId?.let { nid ->
                            Text(
                                "Open note ›",
                                style = MaterialTheme.typography.labelLarge,
                                color = accent,
                                modifier = Modifier
                                    .clickable { onOpenNote(nid) }
                                    .padding(8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
