package app.bosk.ui.screens.flashcardgraph

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bosk.core.graph.GraphNode
import app.bosk.ui.screens.graph.rememberForceLayout
import kotlin.math.sqrt

// Green terminal-matching palette (mirrors the AI interface) so the two "green" surfaces feel related.
private val FgBright = Color(0xFF8FE388)   // flashcard-owning notes
private val FgDim = Color(0xFF4E7A4A)      // similar / neighbour notes
private val FgSelected = Color(0xFFCFFFC8) // selected node ring/fill
private val FgEdge = Color(0xFF6FBF6A)
private val FgPanel = Color(0xFF0E1A0D)

private const val MIN_NODE_R = 3f
private const val MAX_NODE_R = 28f

/**
 * A focused, green-themed graph of the learner's flashcard notes + their similar/connected notes —
 * NOT the whole vault. Lean tooling: pan, zoom, drag, select, plus an AI prompt slide-down at the top
 * that pulls in similar notes. Reuses the shared force-layout physics over a scoped node set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardGraphScreen(
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit = {},
    viewModel: FlashcardGraphViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val flashcardIds by viewModel.flashcardNodeIds.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedNodeId.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() } }

    var promptOpen by remember { mutableStateOf(false) }
    var promptText by remember { mutableStateOf("") }
    val sendPrompt = {
        if (promptText.isNotBlank() && !busy) { viewModel.runPrompt(promptText); promptText = "" }
    }

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

    fun radiusOf(n: GraphNode) = (10f + sqrt(n.size) * 5f)
    fun screenRadiusOf(n: GraphNode) = (radiusOf(n) * scale).coerceIn(MIN_NODE_R, MAX_NODE_R)
    fun screenOf(n: GraphNode): Offset {
        val p = simPos[n.id] ?: Offset(n.x, n.y)
        return Offset(canvasW / 2f + offset.x + p.x * scale, canvasH / 2f + offset.y + p.y * scale)
    }

    val labelPaint = remember {
        android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER }
    }
    val labelArgb = FgBright.toArgb()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FgPanel,
                    titleContentColor = FgBright,
                    navigationIconContentColor = FgBright,
                    actionIconContentColor = FgBright,
                ),
                title = { Text("flashcard-graph", fontFamily = FontFamily.Monospace) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { promptOpen = !promptOpen }) {
                        Icon(Icons.Outlined.Search, contentDescription = "AI prompt")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color(0xFF06120A))) {
            if (snapshot.nodes.isEmpty()) {
                Text(
                    "No flashcard notes yet. Generate flashcards from a note, then load the graph.",
                    color = FgDim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
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
                    drawLine(
                        color = FgEdge.copy(alpha = 0.20f + 0.5f * e.strength),
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
                    val isFlashcard = n.id in flashcardIds
                    val isSelected = n.id == selectedId
                    val fill = when {
                        isSelected -> FgSelected
                        isFlashcard -> FgBright
                        else -> FgDim
                    }
                    drawCircle(color = fill, radius = r, center = center)
                    if (isSelected) {
                        drawCircle(color = FgSelected, radius = r + 4f, center = center, style = Stroke(width = 2f))
                    }
                    // Names only close-up (matches the vault graph's default render distance).
                    if (scale >= app.bosk.core.settings.DEFAULT_LABEL_MIN_SCALE && n.label.isNotBlank()) {
                        drawContext.canvas.nativeCanvas.drawText(
                            n.label.take(24),
                            center.x,
                            center.y + r + 13.sp.toPx(),
                            labelPaint.apply { color = labelArgb },
                        )
                    }
                }
            }

            // AI prompt slide-down — pulls "similar notes" into the scope.
            AnimatedVisibility(
                visible = promptOpen,
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .background(FgPanel, RoundedCornerShape(10.dp))
                        .border(1.dp, FgDim, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("❯", color = FgBright, fontFamily = FontFamily.Monospace)
                    BasicTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = FgBright,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        ),
                        cursorBrush = Brush.verticalGradient(listOf(FgBright, FgBright)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { sendPrompt() }),
                        modifier = Modifier.weight(1f),
                    )
                    if (busy) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = FgBright, modifier = Modifier.size(16.dp))
                    } else {
                        IconButton(onClick = sendPrompt) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Find similar", tint = FgBright)
                        }
                    }
                }
            }

            // Selected-node card (tap to open the underlying note).
            val selected = snapshot.nodes.firstOrNull { it.id == selectedId }
            if (selected != null) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp)
                        .background(FgPanel, RoundedCornerShape(10.dp))
                        .border(1.dp, FgDim, RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        selected.label.ifBlank { "Untitled" },
                        color = FgBright,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (selected.id in flashcardIds) "▤ has flashcards" else "~ related note",
                            color = FgDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                        )
                        selected.noteId?.let { nid ->
                            Text(
                                "open note ▸",
                                color = FgBright,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .clickable { onOpenNote(nid) },
                            )
                        }
                    }
                }
            }
        }
    }
}
