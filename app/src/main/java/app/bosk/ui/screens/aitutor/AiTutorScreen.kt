package app.bosk.ui.screens.aitutor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bosk.core.notes.Markdown
import app.bosk.core.notes.Note

/**
 * The AI Tutor: a notes-grounded study chat. Opens from Home and Notes. The "Context" action
 * surfaces the Compiler-queued notes and today's Daily Roadmap to attach into the conversation.
 * When [openWithRoadmap] is true it opens with the roadmap pinned and a guiding greeting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiTutorScreen(
    onBack: () -> Unit = {},
    onOpenNote: (Long) -> Unit = {},
    openWithRoadmap: Boolean = false,
    autoNoteId: Long = -1L,
    viewModel: AiTutorViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.start(openWithRoadmap, autoNoteId) }

    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val contextOpen by viewModel.contextOpen.collectAsStateWithLifecycle()
    val compilerNotes by viewModel.compilerNotes.collectAsStateWithLifecycle()
    val attachedIds by viewModel.attachedNoteIds.collectAsStateWithLifecycle()
    val roadmap by viewModel.savedRoadmap.collectAsStateWithLifecycle()
    val roadmapAttached by viewModel.roadmapAttached.collectAsStateWithLifecycle()
    val preloadedNote by viewModel.preloadedNote.collectAsStateWithLifecycle()
    val commands by viewModel.commands.collectAsStateWithLifecycle()
    val noteQuery by viewModel.noteQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val attachedNotes by viewModel.attachedNotes.collectAsStateWithLifecycle()
    val vaultNotes by viewModel.vaultNotes.collectAsStateWithLifecycle()
    val quiz by viewModel.quiz.collectAsStateWithLifecycle()
    val missedAvailable by viewModel.missedAvailable.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    val attachedCount = attachedIds.size +
        (if (roadmapAttached && roadmap != null) 1 else 0) +
        (if (preloadedNote != null) 1 else 0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Tutor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearSession() }) {
                        Icon(
                            Icons.Outlined.RestartAlt,
                            contentDescription = "New session",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { viewModel.openContext() }) {
                        Icon(
                            Icons.Outlined.Layers,
                            contentDescription = "Context",
                            tint = if (attachedCount > 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (attachedCount > 0) {
                Text(
                    "$attachedCount item${if (attachedCount == 1) "" else "s"} attached as context",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages) { msg ->
                    TutorBubble(
                        msg = msg,
                        onSaveNote = { viewModel.saveReplyToNote(msg.text) },
                        onMakeCards = { viewModel.cardsFromReply(msg.text) },
                    )
                }
                if (busy) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(
                                "thinking…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Live quiz scoreboard — question position + running score.
            quiz?.let { q ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Question ${(q.index + 1).coerceAtMost(q.total)} of ${q.total}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "✓ ${q.correct}   ◐ ${q.partial}   ✗ ${q.wrong}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            QuickActions(
                roadmapAttached = roadmapAttached && roadmap != null,
                quizActive = quiz != null,
                missedAvailable = missedAvailable,
                onAction = viewModel::action,
                onHint = viewModel::quizHint,
                onSkip = viewModel::quizSkip,
                onStop = viewModel::stopQuiz,
                onCardsFromMisses = viewModel::cardsFromMisses,
            )

            InputBar(enabled = !busy, quizActive = quiz != null, commands = commands, onSend = viewModel::send)
        }
    }

    if (contextOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeContext() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            ContextSheet(
                compilerNotes = compilerNotes,
                vaultNotes = vaultNotes,
                attachedIds = attachedIds,
                attachedNotes = attachedNotes,
                hasRoadmap = roadmap != null,
                roadmapAttached = roadmapAttached,
                noteQuery = noteQuery,
                searchResults = searchResults,
                onNoteQuery = viewModel::setNoteQuery,
                onToggleNote = viewModel::toggleNoteAttached,
                onOpenNote = onOpenNote,
                onToggleRoadmap = viewModel::toggleRoadmapAttached,
            )
        }
    }
}

@Composable
private fun TutorBubble(
    msg: TutorMessage,
    onSaveNote: () -> Unit = {},
    onMakeCards: () -> Unit = {},
) {
    val isUser = msg.role == TutorRole.USER
    val isSystem = msg.role == TutorRole.SYSTEM
    val bg = when {
        isUser -> MaterialTheme.colorScheme.primary
        isSystem -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val fg = when {
        isUser -> MaterialTheme.colorScheme.onPrimary
        isSystem -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            // Tutor bubbles render inline markdown (**bold**, *italic*, `code`) instead of raw markers.
            val body = if (isUser || isSystem) androidx.compose.ui.text.AnnotatedString(msg.text)
            else remember(msg.text) { inlineMarkdown(msg.text) }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = fg)

            // Turn a finished tutor reply into lasting material: a note, or flashcards.
            if (!isUser && !isSystem && msg.text.isNotBlank()) {
                Row(
                    Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "Save note",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onSaveNote() },
                    )
                    Text(
                        "Make cards",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onMakeCards() },
                    )
                }
            }
        }
    }
}

/** Style **bold** / *italic* / `code` inline (per line), so tutor replies read cleanly. */
private fun inlineMarkdown(text: String): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        text.split("\n").forEachIndexed { i, line ->
            if (i > 0) append("\n")
            Markdown.inlineSegments(line).forEach { seg ->
                val style = when {
                    seg.code -> androidx.compose.ui.text.SpanStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                    else -> androidx.compose.ui.text.SpanStyle(
                        fontWeight = if (seg.bold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (seg.italic) androidx.compose.ui.text.font.FontStyle.Italic
                        else androidx.compose.ui.text.font.FontStyle.Normal,
                    )
                }
                pushStyle(style)
                append(seg.text)
                pop()
            }
        }
    }

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun QuickActions(
    roadmapAttached: Boolean,
    quizActive: Boolean,
    missedAvailable: Boolean,
    onAction: (TutorAction) -> Unit,
    onHint: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
    onCardsFromMisses: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (quizActive) {
            // Mid-quiz controls replace the study prompts.
            ActionChip("Hint", onHint)
            ActionChip("Skip", onSkip)
            ActionChip("Stop quiz", onStop)
        } else {
            TutorAction.entries.forEach { action ->
                if (action == TutorAction.PLAN && !roadmapAttached) return@forEach
                ActionChip(action.label) { onAction(action) }
            }
            if (missedAvailable) ActionChip("Cards from misses", onCardsFromMisses)
        }
    }
}

@Composable
private fun InputBar(
    enabled: Boolean,
    quizActive: Boolean,
    commands: List<app.bosk.core.commands.CommandNote.CommandDoc>,
    onSend: (String) -> Unit,
) {
    // TextFieldValue so a palette pick places the cursor AFTER the inserted command.
    var text by remember { mutableStateOf(TextFieldValue("")) }
    fun submit() {
        if (text.text.isNotBlank()) { onSend(text.text); text = TextFieldValue("") }
    }
    Column(Modifier.fillMaxWidth()) {
        // Command palette appears once the learner starts a slash command.
        if (text.text.startsWith("/")) {
            app.bosk.ui.components.CommandPaletteRow(
                commands = app.bosk.core.commands.CommandNote.filter(commands, text.text),
                onPick = { text = TextFieldValue(it, selection = TextRange(it.length)) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        if (quizActive) "Type your answer…  (hint / skip / stop)"
                        else "Ask your tutor…  (type / for commands)",
                    )
                },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() }),
            )
            IconButton(onClick = { submit() }, enabled = enabled && text.text.isNotBlank()) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (enabled && text.text.isNotBlank()) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ContextSheet(
    compilerNotes: List<Note>,
    vaultNotes: List<Note>,
    attachedIds: Set<Long>,
    attachedNotes: List<Note>,
    hasRoadmap: Boolean,
    roadmapAttached: Boolean,
    noteQuery: String,
    searchResults: List<Note>,
    onNoteQuery: (String) -> Unit,
    onToggleNote: (Long) -> Unit,
    onOpenNote: (Long) -> Unit,
    onToggleRoadmap: () -> Unit,
) {
    // Lazy so the WHOLE vault is browsable here without cost — scroll through and attach any
    // note to the conversation (search narrows; sections below browse).
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Tutor context", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }

        // Attach ANY note — search the whole vault by title…
        item {
            OutlinedTextField(
                value = noteQuery,
                onValueChange = onNoteQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search notes to attach…") },
                singleLine = true,
            )
        }
        items(searchResults, key = { "search-${it.id}" }) { n ->
            ContextRow(
                label = n.title.ifBlank { "Untitled note" },
                attached = n.id in attachedIds,
                enabled = true,
                leading = null,
                onToggle = { onToggleNote(n.id) },
                onOpen = { onOpenNote(n.id) },
            )
        }

        // Everything currently attached that isn't in the Compiler list (from search/browse).
        val extraAttached = attachedNotes.filter { a -> compilerNotes.none { it.id == a.id } }
        if (extraAttached.isNotEmpty() && noteQuery.isBlank()) {
            item {
                Text("Attached", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(extraAttached, key = { "attached-${it.id}" }) { n ->
                ContextRow(
                    label = n.title.ifBlank { "Untitled note" },
                    attached = true,
                    enabled = true,
                    leading = null,
                    onToggle = { onToggleNote(n.id) },
                    onOpen = { onOpenNote(n.id) },
                )
            }
        }

        // Daily roadmap row.
        item {
            Text("Daily Roadmap", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            ContextRow(
                label = if (hasRoadmap) "Today's roadmap" else "No roadmap yet — generate one on Home",
                attached = roadmapAttached && hasRoadmap,
                enabled = hasRoadmap,
                leading = Icons.Outlined.Map,
                onToggle = { if (hasRoadmap) onToggleRoadmap() },
                onOpen = null,
            )
        }

        // Compiler notes.
        item {
            Text("Compiler notes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (compilerNotes.isEmpty()) {
            item {
                Text(
                    "Add notes to the Compiler to study them here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(compilerNotes, key = { "compiler-${it.id}" }) { n ->
                ContextRow(
                    label = n.title.ifBlank { "Untitled note" },
                    attached = n.id in attachedIds,
                    enabled = true,
                    leading = null,
                    onToggle = { onToggleNote(n.id) },
                    onOpen = { onOpenNote(n.id) },
                )
            }
        }

        // …or browse the WHOLE vault, title-sorted, and attach as you scroll.
        item {
            Text(
                "All notes (${vaultNotes.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(vaultNotes, key = { "vault-${it.id}" }) { n ->
            ContextRow(
                label = n.title.ifBlank { "Untitled note" },
                attached = n.id in attachedIds,
                enabled = true,
                leading = null,
                onToggle = { onToggleNote(n.id) },
                onOpen = { onOpenNote(n.id) },
            )
        }
    }
}

@Composable
private fun ContextRow(
    label: String,
    attached: Boolean,
    enabled: Boolean,
    leading: androidx.compose.ui.graphics.vector.ImageVector?,
    onToggle: () -> Unit,
    onOpen: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled) { onToggle() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (attached) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = if (attached) "Attached" else "Not attached",
            tint = if (attached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (attached) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (onOpen != null) {
            Text(
                "Open",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpen() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
