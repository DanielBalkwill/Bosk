package com.generic.mnemos.ui.screens.aiinterface

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.KeyboardTab
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.generic.mnemos.core.notes.Note
import com.generic.mnemos.core.review.ReviewProgress
import kotlin.math.roundToInt

private val TermGreen = Color(0xFF8FE388)
private val TermDim = Color(0xFF6E7E6A)

/**
 * A terminal-style AI console focused on what to do next. Shows the last 10 notes you touched as
 * ASCII frames, suggests a next step as ghost text, offers selectable commands (/suggest, …) and
 * lets you type a prompt straight to the AI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiInterfaceScreen(
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit = {},
    onOpenFlashcardGraph: () -> Unit = {},
    viewModel: AiInterfaceViewModel = hiltViewModel(),
) {
    val recents by viewModel.recentNotes.collectAsStateWithLifecycle()
    val output by viewModel.output.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val ghost by viewModel.ghost.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val analyzerOpen by viewModel.analyzerOpen.collectAsStateWithLifecycle()
    val selectedCard by viewModel.selectedCard.collectAsStateWithLifecycle()
    val analyzerOutput by viewModel.analyzerOutput.collectAsStateWithLifecycle()
    val analyzerBusy by viewModel.analyzerBusy.collectAsStateWithLifecycle()
    val commands by viewModel.commands.collectAsStateWithLifecycle()

    val history by viewModel.history.collectAsStateWithLifecycle()
    val noteTitles by viewModel.noteTitles.collectAsStateWithLifecycle()
    val pendingConfirm by viewModel.pendingConfirm.collectAsStateWithLifecycle()

    // TextFieldValue (not String) so autofill buttons can place the cursor AFTER the inserted
    // command instead of leaving it wherever it was.
    var input by remember { mutableStateOf(TextFieldValue("")) }
    fun setInput(text: String) { input = TextFieldValue(text, selection = TextRange(text.length)) }
    // ↑-style recall position into history (-1 = not recalling; resets on manual typing).
    var recallIndex by remember { mutableStateOf(-1) }
    val send = {
        if (input.text.isNotBlank() && !busy) { viewModel.run(input.text); setInput(""); recallIndex = -1 }
    }

    // `/open <title>` emits a navigation event → open that note in the editor.
    LaunchedEffect(Unit) { viewModel.openNote.collect { onOpenNote(it) } }

    // Command palette — ranked by relevance to everything typed after "/" (name prefix first,
    // then by-meaning matches). No slash → a leading slice so the row is always useful.
    val palette = remember(commands, input.text) {
        com.generic.mnemos.core.commands.CommandNote.filter(commands, input.text)
    }

    // Note-title completion for command arguments: "/open mi" → matching titles, tap to complete.
    val argMatch = remember(input.text) { Regex("^(/\\S+)\\s+(\\S.*)$").find(input.text) }
    val titleSuggestions = remember(argMatch, noteTitles) {
        val arg = argMatch?.groupValues?.getOrNull(2)?.trim().orEmpty()
        if (arg.length < 2) emptyList()
        else noteTitles.filter { it.contains(arg, ignoreCase = true) && !it.equals(arg, ignoreCase = true) }.take(5)
    }

    val scroll = rememberScrollState()
    LaunchedEffect(output.size) { scroll.animateScrollTo(scroll.maxValue) }

  Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ai-interface", fontFamily = FontFamily.Monospace) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp),
        ) {
            // Recent notes as ASCII frames.
            Text("recent", color = TermDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            if (recents.isEmpty()) {
                Text("  (no recent notes yet)", color = TermDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            } else {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    recents.forEach { note -> AsciiNoteFrame(note, onClick = { onOpenNote(note.id) }) }
                }
            }

            // Flashcard progress overview — animated, tap to open the interactive analyzer.
            ProgressOverview(progress = progress, onClick = { viewModel.openAnalyzer() })

            // Tappable flashcard chips. Tapping one prints the card's contents + stats straight into
            // the terminal below (NOT the editor, NOT the analyzer chat).
            if (progress.recent.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("flashcards — tap to print", color = TermDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, TermGreen, RoundedCornerShape(8.dp))
                            .clickable { onOpenFlashcardGraph() }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text("⊕ flashcard graph", color = TermGreen, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    progress.recent.forEach { card ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, TermDim, RoundedCornerShape(8.dp))
                                .clickable { viewModel.printCard(card) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                "▤ " + card.front.ifBlank { "Card" }.take(18),
                                color = TermGreen,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            // Terminal output.
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .verticalScroll(scroll),
            ) {
                output.forEach { line ->
                    Text(
                        line.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = when (line.kind) {
                            LineKind.USER -> MaterialTheme.colorScheme.primary
                            LineKind.AI -> TermGreen
                            LineKind.SYSTEM -> TermDim
                        },
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = TermGreen, modifier = Modifier.size(14.dp))
                        Text("…", fontFamily = FontFamily.Monospace, color = TermDim)
                    }
                }
            }

            // Note-title completions for the typed command's argument (shown above the palette).
            if (titleSuggestions.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    titleSuggestions.forEach { title ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, TermGreen, RoundedCornerShape(8.dp))
                                .clickable { setInput("${argMatch!!.groupValues[1]} $title") }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text("¶ ${title.take(28)}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TermGreen)
                        }
                    }
                }
            }

            // Selectable command palette (from the editable command note). Tapping a command drops
            // it into the prompt ready for arguments.
            if (palette.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    palette.forEach { cmd ->
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, TermDim, RoundedCornerShape(8.dp))
                                .clickable { setInput(cmd.name + " ") }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(cmd.name, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TermGreen)
                        }
                    }
                }
            }

            // Prompt with ghost-text suggestion.
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, TermDim, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("❯", color = TermGreen, fontFamily = FontFamily.Monospace)
                Box(Modifier.weight(1f)) {
                    if (input.text.isEmpty() && ghost.isNotBlank()) {
                        Text(
                            ghost,
                            color = TermDim,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it; recallIndex = -1 },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                        ),
                        cursorBrush = Brush.verticalGradient(listOf(TermGreen, TermGreen)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // Accept the ghost suggestion.
                if (input.text.isEmpty() && ghost.isNotBlank()) {
                    IconButton(onClick = { setInput(ghost) }) {
                        Icon(Icons.Outlined.KeyboardTab, contentDescription = "Use suggestion", tint = TermDim)
                    }
                }
                // ↑-style recall: each tap steps one further back through this session's commands.
                if (history.isNotEmpty()) {
                    IconButton(onClick = {
                        recallIndex = if (recallIndex < 0) history.lastIndex else (recallIndex - 1).coerceAtLeast(0)
                        setInput(history[recallIndex])
                    }) {
                        Icon(Icons.Outlined.History, contentDescription = "Recall previous command", tint = TermDim)
                    }
                }
                IconButton(onClick = send, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = TermGreen)
                }
            }
        }
    }

    // Swipe right from the left edge to go back to the menu (mirrors the Home → terminal swipe).
    Box(
        Modifier
            .align(Alignment.CenterStart)
            .fillMaxHeight()
            .width(24.dp)
            .pointerInput(Unit) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onHorizontalDrag = { _, amount: Float -> dx += amount },
                    onDragEnd = { if (dx > 36f) onBack() },
                )
            },
    )

    // Destructive commands (/kill, /empty, /bulk, "empty the note") pause here for a go-ahead.
    pendingConfirm?.let { pending ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.cancelPending() },
            title = { Text(pending.summary, fontFamily = FontFamily.Monospace) },
            text = { Text(pending.detail) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { viewModel.confirmPending() }) {
                    Text("Run", color = TermGreen)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { viewModel.cancelPending() }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Interactive flashcard-progress analyzer (opened by tapping the overview).
    if (analyzerOpen) {
        AnalyzerOverlay(
            progress = progress,
            selected = selectedCard,
            output = analyzerOutput,
            busy = analyzerBusy,
            onSelect = viewModel::selectCard,
            onRun = viewModel::runAnalyzer,
            onClose = viewModel::closeAnalyzer,
        )
    }
  }
}

/** Animated flashcard-progress overview (terminal style). Tap to open the analyzer. */
@Composable
private fun ProgressOverview(progress: FlashProgress, onClick: () -> Unit) {
    val target = progress.masteredFraction.coerceIn(0f, 1f)
    val anim by animateFloatAsState(targetValue = target, animationSpec = tween(900), label = "mastery")
    val slots = 18
    val filled = (anim * slots).roundToInt().coerceIn(0, slots)
    val bar = "█".repeat(filled) + "░".repeat(slots - filled)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, TermDim, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Insights, contentDescription = null, tint = TermGreen, modifier = Modifier.size(14.dp))
                Text("flashcard progress", color = TermDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            Text("tap to analyze ▸", color = TermGreen, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
        Text(bar, color = TermGreen, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        Text(
            "mastered ${(anim * 100).roundToInt()}%  ·  ${progress.reviewedCount} reviewed  ·  ${progress.due} due",
            color = TermDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
        )
        if (progress.recent.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                progress.recent.take(24).forEach { c ->
                    val mastered = c.stability >= 7.0
                    Text(
                        if (mastered) "▰" else "▱",
                        color = if (mastered) TermGreen else if (c.lapses > 0) Color(0xFFD98A6A) else TermDim,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

/** Full-screen interactive analyzer: skim recent results, pick one, and converse with the AI about it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalyzerOverlay(
    progress: FlashProgress,
    selected: ReviewProgress?,
    output: List<TerminalLine>,
    busy: Boolean,
    onSelect: (ReviewProgress) -> Unit,
    onRun: (String) -> Unit,
    onClose: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val send = { if (input.isNotBlank() && !busy) { onRun(input); input = "" } }
    val scroll = rememberScrollState()
    LaunchedEffect(output.size) { scroll.animateScrollTo(scroll.maxValue) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("flashcard-analyzer", fontFamily = FontFamily.Monospace, color = TermGreen, fontSize = 14.sp)
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close analyzer", tint = TermDim)
                }
            }

            Text("recent results — tap one to dig in", color = TermDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            if (progress.recent.isEmpty()) {
                Text("  (no reviews yet — practise some cards first)", color = TermDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            } else {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    progress.recent.forEach { c ->
                        val sel = c.id == selected?.id
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, if (sel) TermGreen else TermDim, RoundedCornerShape(8.dp))
                                .clickable { onSelect(c) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                c.front.ifBlank { "Card" }.take(20),
                                color = if (sel) TermGreen else TermDim,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp).verticalScroll(scroll)) {
                if (output.isEmpty()) {
                    Text(
                        "Pick a card above to start. Then explain your thinking or ask for help.",
                        color = TermDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    )
                }
                output.forEach { line ->
                    Text(
                        line.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = when (line.kind) {
                            LineKind.USER -> MaterialTheme.colorScheme.primary
                            LineKind.AI -> TermGreen
                            LineKind.SYSTEM -> TermDim
                        },
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = TermGreen, modifier = Modifier.size(14.dp))
                        Text("…", fontFamily = FontFamily.Monospace, color = TermDim)
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, TermDim, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("❯", color = TermGreen, fontFamily = FontFamily.Monospace)
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    ),
                    cursorBrush = Brush.verticalGradient(listOf(TermGreen, TermGreen)),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = send, enabled = !busy) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = TermGreen)
                }
            }
        }
    }
}

/** A recent note drawn inside an ASCII box frame (monospace). */
@Composable
private fun AsciiNoteFrame(note: Note, onClick: () -> Unit) {
    val w = 14
    val title = note.title.ifBlank { "Untitled" }.take(w).padEnd(w)
    val bar = "─".repeat(w + 2)
    val frame = "┌$bar┐\n│ $title │\n└$bar┘"
    Text(
        frame,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = TermGreen,
        modifier = Modifier.clickable { onClick() },
    )
}
