package com.generic.mnemos.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.generic.mnemos.core.ai.RoadmapPalette
import com.generic.mnemos.core.ai.RoadmapStep
import com.generic.mnemos.core.notes.Mentions
import com.generic.mnemos.core.notes.Note
import com.generic.mnemos.ui.navigation.LocalPageActive
import kotlinx.coroutines.delay

/**
 * The daily command centre — "a premium notebook that breathes". Five zones over the warm-red
 * dark palette: AI command bar, Now Playing + live graph, Now Reading, Today's Queue, (nav bar
 * lives in the host). Data is real (due count, recent note, due queue); the command bar routes
 * free text to the active AI provider and speaks the reply.
 */
@Composable
fun HomeScreen(
    onStartReview: () -> Unit,
    onOpenNote: (Long) -> Unit,
    onOpenGraph: () -> Unit,
    onOpenQueue: () -> Unit = {},
    onOpenAiInterface: () -> Unit = {},
    onOpenAiTutor: (roadmap: Boolean) -> Unit = {},
    onCreateNote: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val graph by viewModel.graphSnapshot.collectAsStateWithLifecycle()
    val notes by viewModel.allNotes.collectAsStateWithLifecycle()
    val canGoBack by viewModel.canGoBack.collectAsStateWithLifecycle()
    val recentNotes by viewModel.recentNotes.collectAsStateWithLifecycle()

    // Back on Home steps through the previously-previewed notes; only intercepts while there's
    // history to walk, so a normal back (exit) still works once you're at the start.
    BackHandler(enabled = canGoBack) { viewModel.goBack() }

    // Voice quick-capture: system speech dialog → dictated text becomes a note → editor opens.
    val voiceLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
    ) { res ->
        val text = res.data
            ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!text.isNullOrBlank()) viewModel.captureVoice(text)
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.openNoteEvents.collect { onOpenNote(it) }
    }

    // Quick switcher — jump to any note (recents when blank, ranked title+FTS matches typed).
    var switcherOpen by remember { mutableStateOf(false) }
    if (switcherOpen) {
        com.generic.mnemos.ui.components.QuickSwitcher(
            onOpenNote = onOpenNote,
            onDismiss = { switcherOpen = false },
        )
    }

  Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Quiet identity mark + a time-of-day greeting line, and quick entries: new note,
        // AI Tutor, terminal-style AI Interface.
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    "Mnemos",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val greeting = remember {
                    when (java.time.LocalTime.now().hour) {
                        in 5..11 -> "Good morning"
                        in 12..17 -> "Good afternoon"
                        in 18..22 -> "Good evening"
                        else -> "Late night"
                    }
                }
                // Tapping the summary line opens the full stats screen (streak, chart, weak decks).
                Text(
                    "$greeting · ${state.dueCount} due · ${state.noteCount} note${if (state.noteCount == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.clickable { onOpenStats() },
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Quick switcher — jump to any note in two taps.
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable { switcherOpen = true }
                        .padding(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = "Quick switcher",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                // Quick capture — jot a thought without leaving Home for the Notes tab.
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable { onCreateNote() }
                        .padding(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.NoteAdd,
                        contentDescription = "New note",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                // Voice capture — dictate a note via the system speech dialog.
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable {
                            runCatching {
                                voiceLauncher.launch(
                                    android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(
                                            android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                            android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                                        )
                                        putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak your note")
                                    },
                                )
                            }
                        }
                        .padding(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.Mic,
                        contentDescription = "Voice note",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable { onOpenAiTutor(false) }
                        .padding(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.School,
                        contentDescription = "AI Tutor",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable { onOpenAiInterface() }
                        .padding(6.dp),
                ) {
                    Icon(
                        Icons.Outlined.Terminal,
                        contentDescription = "AI Interface",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        CommandBar(
            busy = state.aiBusy,
            reply = state.aiReply,
            noteTitles = remember(notes) { notes.map { it.title } },
            onSend = viewModel::runCommand,
            onDismissReply = viewModel::dismissReply,
        )

        // Continue where you left off — the notes opened most recently, newest first.
        JumpBackIn(
            recent = recentNotes,
            currentId = state.nowReading?.id,
            onOpen = onOpenNote,
        )

        DailyRoadmap(
            busy = state.roadmapBusy,
            steps = state.roadmapSteps,
            error = state.roadmapError,
            onGenerate = viewModel::generateRoadmap,
            onDismiss = viewModel::dismissRoadmap,
            onOpenInTutor = { onOpenAiTutor(true) },
        )

        NowPlayingPanel(
            title = state.nowReading?.title ?: "Nothing playing",
            subtitle = state.nowReading?.tag?.takeIf { it.isNotBlank() } ?: "Your ambient queue is quiet",
            graph = graph,
            currentNoteId = state.nowReading?.id,
            playing = state.isPlaying,
            notes = notes,
            onSelectNote = viewModel::selectNote,
            onReplay = viewModel::play,
            onPlayPause = { playing -> if (playing) viewModel.play() else viewModel.pause() },
            onSkip = viewModel::skip,
            onOpenGraph = onOpenGraph,
        )

        state.nowReading?.let { nr ->
            NowReadingCard(
                title = nr.title,
                tag = nr.tag,
                preview = nr.preview,
                onOpen = { onOpenNote(nr.id) },
                onPrev = viewModel::previousNote,
                onNext = viewModel::nextNote,
            )
        }

        TodaysQueue(
            dueCount = state.dueCount,
            items = state.queue,
            onStartReview = onStartReview,
            onManageQueue = onOpenQueue,
        )
    }

    // Visible left-edge handle → AI Interface. A small tab that makes the swipe affordance obvious
    // (tap it, or flick right from it). Kept narrow so it doesn't fight the tab pager or scroll.
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .align(Alignment.CenterStart)
            .width(30.dp)
            .height(140.dp)
            .clip(RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp))
            .background(accent.copy(alpha = 0.20f))
            .border(
                1.dp,
                accent.copy(alpha = 0.35f),
                RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp),
            )
            .clickable { onOpenAiInterface() }
            .pointerInput(Unit) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onHorizontalDrag = { _, amount: Float -> dx += amount },
                    onDragEnd = { if (kotlin.math.abs(dx) > 36f) onOpenAiInterface() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Outlined.Terminal,
                contentDescription = "AI Interface",
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = accent.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// ZONE 0.5 — Jump back in (recently-opened notes)
// ─────────────────────────────────────────────────────────────────────────────

/** A horizontal row of the last-opened notes — one tap resumes exactly where you left off. */
@Composable
private fun JumpBackIn(recent: List<Note>, currentId: Long?, onOpen: (Long) -> Unit) {
    // The currently-previewed note is already front-and-centre in Now Reading — skip it here.
    val items = recent.filter { it.id != currentId }.take(8)
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Jump back in",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { n ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onOpen(n.id) },
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).width(120.dp)) {
                        Text(
                            n.title.ifBlank { "Untitled" },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            n.tags.firstOrNull()?.let { "#$it" } ?: "note",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ZONE 1 — AI command bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CommandBar(
    busy: Boolean,
    reply: String?,
    noteTitles: List<String>,
    onSend: (String) -> Unit,
    onDismissReply: () -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue("")) }
    val accent = MaterialTheme.colorScheme.primary

    // Colour resolved #Note mentions; identity offset mapping (we only restyle, not retext).
    val mentionTransform = remember(noteTitles, accent) {
        VisualTransformation { src ->
            val matches = Mentions.find(src.text, noteTitles)
            val b = AnnotatedString.Builder(src.text)
            matches.forEach { m ->
                b.addStyle(SpanStyle(color = accent, fontWeight = FontWeight.Medium), m.start, m.end)
            }
            TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
        }
    }

    val cursor = value.selection.end
    val active = remember(value) { Mentions.activeQuery(value.text, cursor) }
    val suggestions = if (active != null) Mentions.suggestions(active.query, noteTitles) else emptyList()

    fun insertMention(title: String) {
        val a = active ?: return
        val before = value.text.substring(0, a.start)
        val after = value.text.substring(cursor.coerceAtMost(value.text.length))
        val insert = "#$title "
        val newText = before + insert + after
        value = TextFieldValue(newText, selection = TextRange((before + insert).length))
    }

    var expanded by remember { mutableStateOf(false) }

    Column(
        Modifier.pinchToExpand { expanded = it },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SmallCaps("AI Command")

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(28.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Waveform(active = busy, color = accent)

            Box(Modifier.weight(1f)) {
                if (value.text.isEmpty()) {
                    Text(
                        "Ask anything · #note to add a note's contents",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    visualTransformation = mentionTransform,
                    cursorBrush = Brush.verticalGradient(listOf(accent, accent)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val send = {
                if (value.text.isNotBlank()) { onSend(value.text); value = TextFieldValue("") }
            }
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (busy) MaterialTheme.colorScheme.surface else accent)
                    .clickable(enabled = !busy) { send() },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = accent,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        AnimatedVisibility(visible = active != null && suggestions.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
            ) {
                suggestions.forEach { title ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { insertMention(title) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("#", style = MaterialTheme.typography.titleMedium, color = accent)
                        Text(
                            title.ifBlank { "Untitled" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = reply != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .clickable { onDismissReply() }
                    .padding(14.dp)
                    .animateContentSize(),
            ) {
                Text(
                    reply.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (expanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Three little sine-timed bars — alive while [active], settled otherwise. */
@Composable
private fun Waveform(active: Boolean, color: Color) {
    // Only CREATE the animations while actually waving AND on the visible page. An infinite
    // transition keeps scheduling frames even when its values go unread, so idling on Home was
    // silently animating forever — dispose it entirely instead of just ignoring the value.
    val pageActive = LocalPageActive.current
    val animating = active && pageActive
    val phases: List<androidx.compose.runtime.State<Float>>? = if (animating) {
        val transition = rememberInfiniteTransition(label = "wave")
        listOf(0, 150, 300).map { delayMs ->
            transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = delayMs),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar$delayMs",
            )
        }
    } else null
    Row(
        Modifier.size(width = 20.dp, height = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            val h = phases?.get(i)?.value ?: 0.45f
            Box(
                Modifier
                    .width(3.dp)
                    .height((18.dp) * h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ZONE 1.5 — Daily Roadmap (AI-generated plan for today)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DailyRoadmap(
    busy: Boolean,
    steps: List<RoadmapStep>,
    error: String?,
    onGenerate: () -> Unit,
    onDismiss: () -> Unit,
    onOpenInTutor: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SmallCaps("Daily Roadmap")
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (busy) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary)
                    .clickable(enabled = !busy) { onGenerate() }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                } else {
                    Text(
                        if (steps.isEmpty()) "Generate" else "Regenerate",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
        if (error != null && steps.isEmpty()) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        AnimatedVisibility(visible = steps.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    // Tapping the plan opens it in the AI Tutor (with the roadmap in view).
                    .clickable { onOpenInTutor() }
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Blocks whose time has passed (a later block already started) are crossed out,
                // so the plan reads as a live checklist of where you are in the day. The clock
                // ticks while the card is on screen, so a block crosses itself out when its
                // successor's start time arrives — not only on the next recomposition.
                var nowMinutes by remember {
                    mutableStateOf(java.time.LocalTime.now().let { it.hour * 60 + it.minute })
                }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    while (true) {
                        delay(30_000)
                        nowMinutes = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
                    }
                }
                val pastSteps = remember(steps, nowMinutes) {
                    com.generic.mnemos.core.ai.RoadmapTime.pastStepIndices(steps, nowMinutes)
                }
                steps.forEachIndexed { i, step ->
                    RoadmapStepRow(step, Color(RoadmapPalette.colorForIndex(i)), past = i in pastSteps)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.School,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Open in tutor ›",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        "Clear",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDismiss() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** One colour-coded roadmap step: accent bar + bold coloured time + title/detail + note chips.
 *  [past] blocks (their time is over) render struck-through and dimmed. */
@Composable
private fun RoadmapStepRow(step: RoadmapStep, color: Color, past: Boolean = false) {
    val strike = if (past) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
    val dim = if (past) 0.45f else 1f
    Row(
        Modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(color.copy(alpha = dim)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (step.time.isNotBlank()) {
                Text(
                    step.time,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = color.copy(alpha = dim),
                    textDecoration = strike,
                )
            }
            Text(
                step.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim),
                textDecoration = strike,
            )
            if (step.detail.isNotBlank()) {
                Text(
                    step.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (step.notes.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 1.dp),
                ) {
                    step.notes.take(3).forEach { n ->
                        Text(
                            n,
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(color.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ZONE 2 — Now Playing + live graph preview
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NowPlayingPanel(
    title: String,
    subtitle: String,
    graph: com.generic.mnemos.core.graph.GraphSnapshot,
    currentNoteId: Long?,
    playing: Boolean,
    notes: List<com.generic.mnemos.core.notes.Note>,
    onSelectNote: (Long) -> Unit,
    onReplay: () -> Unit,
    onPlayPause: (playing: Boolean) -> Unit,
    onSkip: () -> Unit,
    onOpenGraph: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .animateContentSize(),
    ) {
        Row(Modifier.fillMaxWidth().height(168.dp)) {
            // Left — controls
            Column(
                Modifier
                    .weight(0.46f)
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ControlButton(Icons.Filled.Replay, "Replay", 40.dp, onReplay)
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            // Driven by real state, so it updates when a sent prompt auto-plays.
                            .clickable { onPlayPause(!playing) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                    ControlButton(Icons.Filled.SkipNext, "Skip", 40.dp, onSkip)
                }
            }

            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outline),
            )

            // Right — mini graph
            Column(
                Modifier
                    .weight(0.54f)
                    .fillMaxHeight()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                SmallCaps("Knowledge Graph")
                HomeGraph(graph, currentNoteId, Modifier.weight(1f).fillMaxWidth())
                Text(
                    "View Full Graph →",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onOpenGraph() }
                        .padding(4.dp),
                )
            }
        }

        // The "upcoming" list = the next notes that prev/next would step to (just the next 10 notes,
        // not a custom queue). Tapping one focuses it.
        val upcoming = remember(notes, currentNoteId) {
            if (notes.isEmpty()) emptyList()
            else {
                val i = notes.indexOfFirst { it.id == currentNoteId }.let { if (it < 0) 0 else it }
                (1..notes.size).map { notes[(i + it) % notes.size] }
                    .distinctBy { it.id }
                    .filter { it.id != currentNoteId }
                    .take(10)
            }
        }
        // Expand to reveal the upcoming notes; tapping an item focuses that note.
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SmallCaps("Upcoming notes · ${upcoming.size}")
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Hide upcoming" else "Show upcoming",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                if (upcoming.isEmpty()) {
                    Text(
                        "No other notes yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp),
                    )
                } else {
                    upcoming.forEach { note ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectNote(note.id) }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            Text(
                                note.title.ifBlank { "Untitled" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlButton(icon: androidx.compose.ui.graphics.vector.ImageVector, cd: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = cd, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ZONE 3 — Now Reading
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NowReadingCard(
    title: String,
    tag: String,
    preview: String,
    onOpen: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val surface = MaterialTheme.colorScheme.surface
    var expanded by remember { mutableStateOf(false) }
    // NOTE: the open-tap lives on the title/preview block only (not the whole card), and the
    // prev/next arrows sit in the header outside it. Keeping these gesture regions separate stops
    // the arrow taps being swallowed by a card-wide clickable (which made cycling feel dead).
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .pinchToExpand { expanded = it }
            .animateContentSize(),
    ) {
        // left accent rail
        Box(
            Modifier
                .width(3.dp)
                .height(if (expanded) 340.dp else 180.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SmallCaps("Now Reading")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Cycle which note is previewed/played (and centred in the mini-graph).
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).clickable { onPrev() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous note",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).clickable { onNext() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next note",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // Tapping the title/preview block opens the full note.
            Column(Modifier.clickable { onOpen() }) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tag.isNotBlank()) {
                    Text(tag, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(8.dp))
                // body preview with a fade-out mask (mask hidden once expanded)
                Box {
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) 20 else 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!expanded) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(22.dp)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, surface))),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineChip("Generate Cards", Icons.Outlined.Style, onOpen)
                OutlineChip("Add Connection", Icons.Outlined.Hub, onOpen)
            }
        }
    }
}

@Composable
private fun OutlineChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ZONE 4 — Today's Queue
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TodaysQueue(
    dueCount: Int,
    items: List<QueueItem>,
    onStartReview: () -> Unit,
    onManageQueue: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val visibleCount = if (expanded) items.size else 3
    Column(
        Modifier.pinchToExpand { expanded = it }.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Today's Queue",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (dueCount > 0) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "$dueCount due",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            // Opens the full queue manager (reorder, alarms & events).
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .clickable { onManageQueue() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text("Queue", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (items.isEmpty()) {
            Text(
                if (dueCount == 0) "You're all caught up. Mnemos will surface more when it's due."
                else "Loading your queue…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            items.take(visibleCount).forEachIndexed { i, item ->
                StaggeredQueueRow(index = i, item = item, overdue = i == 0, onClick = onStartReview)
            }
            if (!expanded && items.size > 3) {
                Text(
                    "Pinch to expand · ${items.size - 3} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StaggeredQueueRow(index: Int, item: QueueItem, overdue: Boolean, onClick: () -> Unit) {
    var visible by remember(item.reviewId) { mutableStateOf(false) }
    LaunchedEffectStagger(index, item.reviewId) { visible = true }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(280), label = "qAlpha")
    val offsetY by animateDpAsState(if (visible) 0.dp else 10.dp, tween(280), label = "qOffset")

    val dot = if (overdue) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha; translationY = offsetY.toPx() }
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
        Column(Modifier.weight(1f)) {
            Text(
                item.front.ifBlank { "Card" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.back,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusPill(if (overdue) "Overdue" else "Due", overdue)
    }
}

@Composable
private fun StatusPill(text: String, accent: Boolean) {
    val bg = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (accent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Pinch a home card to expand it inline (spread fingers to open, pinch to close) — reveals more
 * content without leaving the page. Strictly a **two-finger** gesture: single-finger drags are
 * left untouched so the horizontal pager (swipe between tabs) and vertical scroll stay fully
 * responsive over these cards. (The old detectTransformGestures consumed one-finger pans too,
 * which made swiping away from Home feel sluggish.)
 */
private fun Modifier.pinchToExpand(onChange: (Boolean) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                val zoom = event.calculateZoom()
                if (zoom > 1.02f) onChange(true) else if (zoom < 0.98f) onChange(false)
                event.changes.forEach { it.consume() } // only consume during a real pinch
            }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
private fun SmallCaps(text: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(letterSpacing = 1.5.sp, fontWeight = FontWeight.Medium)) {
                append(text.uppercase())
            }
        },
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Run [block] once after a per-index stagger delay (keyed so re-use restarts cleanly). */
@Composable
private fun LaunchedEffectStagger(index: Int, key: Any, block: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(key) {
        delay(index * 80L)
        block()
    }
}
