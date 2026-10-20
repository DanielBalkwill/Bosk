package com.generic.mnemos.ui.screens.notes

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddLink
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatClear
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import coil.compose.AsyncImage
import com.generic.mnemos.core.notes.Frontmatter
import com.generic.mnemos.core.notes.Markdown
import com.generic.mnemos.core.notes.MarkdownEdit
import com.generic.mnemos.core.notes.Mentions
import com.generic.mnemos.core.notes.Redaction
import com.generic.mnemos.core.notes.WikiLinks
import com.generic.mnemos.ui.components.AiResultSheet
import com.generic.mnemos.ui.components.rememberHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    onBack: () -> Unit,
    onOpenNote: (Long) -> Unit = {},
    onOpenConnectionsGraph: () -> Unit = {},
    viewModel: NoteEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val connections by viewModel.connectionCount.collectAsStateWithLifecycle()
    val noteTitles by viewModel.noteTitles.collectAsStateWithLifecycle()
    val noteIdsByTitle by viewModel.noteIdsByTitle.collectAsStateWithLifecycle()
    val recentAiCommands by viewModel.recentAiCommands.collectAsStateWithLifecycle()
    val slashCommands by viewModel.commands.collectAsStateWithLifecycle()
    val versions by viewModel.versions.collectAsStateWithLifecycle()
    var versionsOpen by remember { mutableStateOf(false) }
    val fontSp by viewModel.fontSp.collectAsStateWithLifecycle()
    // Live font size (drives the editor immediately while dragging; persisted on release).
    var liveFont by remember(fontSp) { mutableStateOf(fontSp) }
    var fontControlOpen by remember { mutableStateOf(false) }
    // Redact mode: while ON, tapping a word redacts it instantly and dragging redacts the span.
    var redactMode by remember { mutableStateOf(false) }
    // The editor's DEFAULT is Obsidian-style live preview: markdown renders in place and stays
    // fully editable (the caret's line shows raw source). sourceMode flips to all-raw markdown.
    var sourceMode by remember { mutableStateOf(false) }
    // Whether the body field has focus — drives the above-keyboard formatting accessory bar.
    var bodyFocused by remember { mutableStateOf(false) }
    // Explain-by-area: while ON, the body swaps to a drag-to-select surface; releasing the drag
    // sends every line the rectangle touched to the AI for a plain-language explanation.
    var explainSelect by remember { mutableStateOf(false) }
    // Focus mode: distraction-free writing — all chrome (top bar, toolbars, tags, banners, the
    // AI tab) hides, leaving just title + body. A small ⤢ button (or back) exits.
    var focusMode by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Note-level actions (top-bar ⋮ menu): share via the system sheet, duplicate, move to trash.
    var noteMenuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun shareNote() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, state.title)
            putExtra(Intent.EXTRA_TEXT, viewModel.shareableText())
        }
        context.startActivity(Intent.createChooser(send, "Share note"))
    }

    // Outline (jump-to-heading) — recomputed only when the body changes.
    val outline = remember(state.body) { Markdown.outline(state.body) }
    var outlineOpen by remember { mutableStateOf(false) }

    // Save consistently whether the user leaves via the arrow or the system back gesture.
    // In focus mode, back first returns to the normal editor instead of leaving the note.
    BackHandler {
        if (focusMode) focusMode = false else { viewModel.save(); onBack() }
    }

    Scaffold(
        topBar = {
            if (!focusMode) TopAppBar(
                title = {
                    if (state.saving) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.padding(end = 8.dp).size(16.dp),
                            )
                            Text("Saving…", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Text("Note", style = MaterialTheme.typography.titleMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.save(); onBack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { noteMenuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Note actions")
                    }
                    DropdownMenu(expanded = noteMenuOpen, onDismissRequest = { noteMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Outlined.IosShare, contentDescription = null) },
                            onClick = { noteMenuOpen = false; viewModel.save(); shareNote() },
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            leadingIcon = { Icon(Icons.Outlined.FileCopy, contentDescription = null) },
                            onClick = {
                                noteMenuOpen = false
                                viewModel.duplicateNote { id -> onOpenNote(id) }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Move to trash") },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                            onClick = { noteMenuOpen = false; confirmDelete = true },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
        // The body is edited as a TextFieldValue so we can act on the user's selection (redact).
        var bodyTfv by remember { mutableStateOf(TextFieldValue(state.body)) }
        LaunchedEffect(state.body) {
            // Pull in external body changes (e.g. the AI streaming a rewrite) without clobbering edits.
            if (state.body != bodyTfv.text) {
                bodyTfv = bodyTfv.copy(text = state.body, selection = TextRange(state.body.length))
            }
        }
        val imagePicker = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri -> uri?.let { viewModel.attachImage(it) } }

        // Redact mode: a tap (collapsed selection) redacts the word; a drag selects a span. Debounced
        // so a drag applies once it settles and a tap feels instant — never mid-gesture.
        var pendingRedact by remember { mutableStateOf<TextRange?>(null) }
        LaunchedEffect(pendingRedact, redactMode) {
            val sel = pendingRedact ?: return@LaunchedEffect
            if (!redactMode) return@LaunchedEffect
            delay(if (sel.collapsed) 0L else 160L)
            if (pendingRedact != sel) return@LaunchedEffect // superseded by a newer gesture
            if (sel.collapsed) viewModel.redactWordAt(sel.start) else viewModel.redactRange(sel.start, sel.end)
            haptics.confirm()
            pendingRedact = null
        }

        // Apply a markdown formatting action to the body field's current selection.
        fun applyFormat(f: EditorFormat) {
            val s = bodyTfv.selection.min
            val e = bodyTfv.selection.max
            val edit = when (f) {
                EditorFormat.BOLD -> MarkdownEdit.toggleWrap(bodyTfv.text, s, e, "**")
                EditorFormat.ITALIC -> MarkdownEdit.toggleWrap(bodyTfv.text, s, e, "*")
                EditorFormat.STRIKE -> MarkdownEdit.toggleWrap(bodyTfv.text, s, e, "~~")
                EditorFormat.CODE -> MarkdownEdit.toggleWrap(bodyTfv.text, s, e, "`")
                EditorFormat.HEADING -> MarkdownEdit.toggleLinePrefix(bodyTfv.text, s, "# ")
                EditorFormat.BULLET -> MarkdownEdit.toggleLinePrefix(bodyTfv.text, s, "- ")
                EditorFormat.QUOTE -> MarkdownEdit.toggleLinePrefix(bodyTfv.text, s, "> ")
                EditorFormat.CHECKBOX -> MarkdownEdit.toggleCheckbox(bodyTfv.text, s)
                EditorFormat.LINK -> MarkdownEdit.toggleWrapPair(bodyTfv.text, s, e, "[[", "]]")
                EditorFormat.DIVIDER -> MarkdownEdit.insertDivider(bodyTfv.text, s)
                EditorFormat.CLEAR -> MarkdownEdit.clearFormatting(bodyTfv.text, s, e)
            }
            bodyTfv = TextFieldValue(edit.text, selection = TextRange(edit.selStart, edit.selEnd))
            viewModel.onBodyChange(edit.text)
            viewModel.recordHistory()
        }

        // Live cursor-context: which formatting is active where the caret sits. Drives the ON/OFF
        // state of every formatting toggle in the toolbar + accessory bar. Line-local, so it's
        // cheap enough to recompute per keystroke.
        val fmt = remember(bodyTfv.text, bodyTfv.selection) {
            val caret = bodyTfv.selection.min
            val inline = Markdown.inlineStateAt(bodyTfv.text, caret)
            val checkbox = MarkdownEdit.hasCheckbox(bodyTfv.text, caret)
            FormatStates(
                bold = inline.bold,
                italic = inline.italic,
                code = inline.code,
                strike = inline.strike,
                heading = MarkdownEdit.hasLinePrefix(bodyTfv.text, caret, "# "),
                // A task line also starts with "- " — don't light Bullet up for it too.
                bullet = !checkbox && MarkdownEdit.hasLinePrefix(bodyTfv.text, caret, "- "),
                quote = MarkdownEdit.hasLinePrefix(bodyTfv.text, caret, "> "),
                checkbox = checkbox,
            )
        }

        // Clipboard tools. Copy with no selection copies the whole note body.
        val clipboard = LocalClipboardManager.current
        fun copySelectionOrAll() {
            val sel = bodyTfv.selection
            val text = if (sel.collapsed) bodyTfv.text else bodyTfv.text.substring(sel.min, sel.max)
            if (text.isNotEmpty()) clipboard.setText(AnnotatedString(text))
        }
        fun cutSelection() {
            val sel = bodyTfv.selection
            if (sel.collapsed) return
            clipboard.setText(AnnotatedString(bodyTfv.text.substring(sel.min, sel.max)))
            val newText = bodyTfv.text.removeRange(sel.min, sel.max)
            bodyTfv = TextFieldValue(newText, selection = TextRange(sel.min))
            viewModel.onBodyChange(newText)
            viewModel.recordHistory()
        }
        fun pasteAtCursor() {
            val clip = clipboard.getText()?.text.orEmpty()
            if (clip.isEmpty()) return
            val sel = bodyTfv.selection
            val newText = bodyTfv.text.replaceRange(sel.min, sel.max, clip)
            bodyTfv = TextFieldValue(newText, selection = TextRange(sel.min + clip.length))
            viewModel.onBodyChange(newText)
            viewModel.recordHistory()
        }

        // Find & replace — case-insensitive, wraps around; lives in a bar under the toolbar.
        var findOpen by remember { mutableStateOf(false) }
        var findQ by remember { mutableStateOf("") }
        var replQ by remember { mutableStateOf("") }
        fun findNext() {
            if (findQ.isBlank()) return
            val from = bodyTfv.selection.max
            val idx = bodyTfv.text.indexOf(findQ, from, ignoreCase = true)
                .let { if (it >= 0) it else bodyTfv.text.indexOf(findQ, 0, ignoreCase = true) }
            if (idx >= 0) bodyTfv = bodyTfv.copy(selection = TextRange(idx, idx + findQ.length))
        }
        fun replaceCurrent() {
            if (findQ.isBlank()) return
            val sel = bodyTfv.selection
            if (!sel.collapsed && bodyTfv.text.substring(sel.min, sel.max).equals(findQ, ignoreCase = true)) {
                val newText = bodyTfv.text.replaceRange(sel.min, sel.max, replQ)
                bodyTfv = TextFieldValue(newText, selection = TextRange(sel.min + replQ.length))
                viewModel.onBodyChange(newText)
                viewModel.recordHistory()
            }
            findNext()
        }
        fun replaceAll() {
            if (findQ.isBlank()) return
            val newText = bodyTfv.text.replace(findQ, replQ, ignoreCase = true)
            if (newText != bodyTfv.text) {
                bodyTfv = TextFieldValue(newText, selection = TextRange(newText.length.coerceAtMost(newText.length)))
                viewModel.onBodyChange(newText)
                viewModel.recordHistory()
            }
        }

        // Pinned, always-visible toolbar of small icon buttons — every toggleable tool shows a
        // live ON/OFF state (formatting toggles reflect the caret's context).
        if (!focusMode) EditorToolbar(
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            canCut = !bodyTfv.selection.collapsed,
            canPaste = clipboard.hasText(),
            fmt = fmt,
            redactMode = redactMode,
            onUndo = viewModel::undo,
            onRedo = viewModel::redo,
            onCopy = { copySelectionOrAll() },
            onCut = { cutSelection() },
            onPaste = { pasteAtCursor() },
            onFormat = { applyFormat(it) },
            onToggleRedact = { redactMode = !redactMode; pendingRedact = null },
            onImage = { imagePicker.launch(arrayOf("image/*")) },
            onToggleFont = { fontControlOpen = !fontControlOpen },
            findOpen = findOpen,
            onToggleFind = { findOpen = !findOpen },
            hasVersions = versions.isNotEmpty(),
            onHistory = { versionsOpen = true },
            hasOutline = outline.isNotEmpty(),
            onOutline = { outlineOpen = !outlineOpen },
            // Entering focus clears every mode whose controls are about to disappear.
            onFocusMode = {
                focusMode = true
                fontControlOpen = false
                findOpen = false
                redactMode = false
                explainSelect = false
            },
        )

        // The AI toolbar — its own dedicated row: the app's headline actions, always one tap away.
        if (!focusMode) AiToolbar(
            onAnalyze = viewModel::analyzeNote,
            onSummarise = viewModel::summarise,
            onCards = viewModel::generateCards,
            onConnections = viewModel::findConnections,
            // Explain works on an AREA: tap, then drag over the part of the note to explain.
            // (A blank note falls back to explaining the title.)
            onExplain = { if (state.body.isBlank()) viewModel.explain() else explainSelect = true },
            onCloze = viewModel::clozeFromRedactions,
        )

        // Appears once Connect has run (or the note already has links): opens a scoped graph of
        // this note and its nearest connections, AI suggestions highlighted in gold.
        AnimatedVisibility(visible = (connections > 0 || state.connectionsSuggested) && !focusMode) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.save(); onOpenConnectionsGraph() },
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.Hub,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "View connections graph" +
                            if (connections > 0) " ($connections)" else "",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // Find & replace bar (under the toolbar, above the note content).
        AnimatedVisibility(visible = findOpen && !focusMode) {
            Surface(tonalElevation = 1.dp, color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = findQ,
                        onValueChange = { findQ = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Find") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = replQ,
                        onValueChange = { replQ = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Replace") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Next",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { findNext() }.padding(4.dp),
                    )
                    Text(
                        "One",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { replaceCurrent() }.padding(4.dp),
                    )
                    Text(
                        "All",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { replaceAll() }.padding(4.dp),
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {

        // Link detection — lifted to the page scope so the pinned bottom bar (above the keyboard)
        // and the inline `!` suggestion menu both read the same cursor state. Remembered:
        // linkAt/bareActiveQuery scan against EVERY note title, so they must not re-run on
        // unrelated recompositions (saving spinner, AI state) — only when text/caret/titles move.
        val cursor = bodyTfv.selection.end
        val (openTitle, openId, bareActive, bracketActive, linkSuggestions) =
            remember(bodyTfv.text, bodyTfv.selection, noteTitles, noteIdsByTitle) {
                val collapsed = bodyTfv.selection.collapsed
                val title = if (collapsed) WikiLinks.linkAt(bodyTfv.text, cursor, noteTitles) else null
                val id = title?.let { noteIdsByTitle[it.trim().lowercase()] }
                // Bracket is probed first: inside `![[…` the bare `!` scan would swallow the
                // brackets into its query and neither menu would match anything.
                val bracket = if (collapsed && id == null) WikiLinks.bracketActiveQuery(bodyTfv.text, cursor) else null
                val bare = if (collapsed && id == null && bracket == null) WikiLinks.bareActiveQuery(bodyTfv.text, cursor) else null
                val query = bracket?.second ?: bare?.second
                val suggestions = if (query != null) Mentions.suggestions(query, noteTitles) else emptyList()
                LinkContext(title, id, bare, bracket, suggestions)
            }

        val pageScroll = rememberScrollState()
        val scrollbarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        Column(
            // Scrollable page so long notes (and the fields below) can be scrolled.
            // top padding leaves room for the collapsed AI pull-tab hanging from the top.
            // imePadding shrinks the viewport when the keyboard opens, so the field's own
            // bring-cursor-into-view can always scroll the typing point above the keyboard.
            // The scroll modifier is UNCONDITIONAL: swapping it out of the chain (as explain-
            // select briefly did) resets the position to the top on every mode flip. During
            // explain-select the drag detector consumes its gesture, so selection wins over
            // scrolling on the text itself while everything else still scrolls.
            Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 44.dp)
                .imePadding()
                .verticalScrollbar(pageScroll, scrollbarColor)
                .verticalScroll(pageScroll),
        ) {
            // Redact-mode hint, else a quick reactive connection indicator (instant DB count).
            if (!focusMode) Text(
                text = when {
                    redactMode -> "Redact mode: tap a word, or drag across text"
                    state.id <= 0 -> "Not saved yet"
                    connections == 0 -> "No connections yet"
                    connections == 1 -> "1 connection"
                    else -> "$connections connections"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (redactMode || connections > 0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )

            val transparentColors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            )

            TextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Title", style = MaterialTheme.typography.headlineMedium) },
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontSize = (liveFont * 1.6f).sp),
                singleLine = true,
                colors = transparentColors,
            )

            // Tags live at the top of every note, right under the title: an add box plus the
            // note's tags as highlighted chips (✕ to remove). Frontmatter `tags:` properties
            // from the note text show here too (no ✕ — they're edited in the text). Inline
            // #tags in the body still merge at save time as before.
            val frontmatterTags = remember(state.body) { Frontmatter.tags(state.body) }
            if (!focusMode) TagsRow(
                tagsText = state.tagsText,
                frontmatterTags = frontmatterTags,
                onTagsChange = viewModel::onTagsChange,
            )

            // Highlight [[wiki-links]] / !links and grey-out redacted spans (markers hidden).
            val accent = MaterialTheme.colorScheme.primary
            val redactBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
            val codeBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            val quoteCol = MaterialTheme.colorScheme.onSurfaceVariant
            val dimCol = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            // CRITICAL: the transformation must be a pure function of the TEXT — never of the
            // selection or focus. The old caret-line-raw behaviour rebuilt it as the selection
            // moved, replacing the visual text mid-gesture: selection handles fought a shifting
            // layout and the keyboard's composing session desynced ("select bugs out, typing
            // does nothing"). Markdown now renders live everywhere; the source FAB shows raw.
            val bodyTransform = if (sourceMode) {
                remember(noteTitles, accent, redactBg) {
                    EditorVisualTransformation(noteTitles, accent, redactBg)
                }
            } else {
                remember(noteTitles, accent, redactBg, codeBg, quoteCol, dimCol, liveFont) {
                    LiveMarkdownTransformation(noteTitles, accent, redactBg, codeBg, quoteCol, dimCol, liveFont)
                }
            }

            // Obsidian-style READING MODE: while the body isn't being edited (not focused, not
            // source view, not redacting), the note shows as a fully-rendered MarkdownView —
            // inline images in flow, tappable checkboxes, proper blocks. Tapping the text
            // switches to the editable field and focuses it.
            var editRequested by remember { mutableStateOf(false) }
            val bodyFocusRequester = remember { FocusRequester() }
            val reading = !sourceMode && !redactMode && !bodyFocused && !editRequested &&
                !explainSelect && state.body.isNotBlank()

            if (explainSelect) {
                // Explain-by-area: the body becomes a plain drag surface; the drag rectangle
                // highlights whole lines and releasing sends their text off to be explained.
                ExplainSelectArea(
                    body = state.body,
                    fontSp = liveFont,
                    onSelected = { text ->
                        explainSelect = false
                        viewModel.explainSelection(text)
                    },
                    onCancel = { explainSelect = false },
                )
            } else if (reading) {
                MarkdownView(
                    body = state.body,
                    fontSp = liveFont,
                    imageFile = viewModel::imageFile,
                    isImageName = viewModel::isImageName,
                    onToggleTask = { i ->
                        val off = taskLineOffset(state.body, i)
                        if (off >= 0) {
                            val edit = MarkdownEdit.toggleCheckbox(state.body, off)
                            viewModel.onBodyChange(edit.text)
                            viewModel.recordHistory()
                            haptics.confirm()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 320.dp)
                        .padding(vertical = 4.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { editRequested = true },
                )
            } else {
                LaunchedEffect(editRequested) { if (editRequested) bodyFocusRequester.requestFocus() }
                TextField(
                    value = bodyTfv,
                    onValueChange = { newTfv ->
                        // Tapping a rendered ☐/☑ from ANOTHER line toggles it directly instead of
                        // just moving the caret (matching Obsidian's live-preview checkboxes).
                        val tappedBox = !sourceMode && !redactMode &&
                            newTfv.text == bodyTfv.text && newTfv.selection.collapsed &&
                            lineIndexOf(newTfv.text, newTfv.selection.start) !=
                            lineIndexOf(bodyTfv.text, bodyTfv.selection.min) &&
                            caretOnTaskBox(newTfv.text, newTfv.selection.start)
                        if (tappedBox) {
                            val edit = MarkdownEdit.toggleCheckbox(newTfv.text, newTfv.selection.start)
                            bodyTfv = TextFieldValue(edit.text, selection = TextRange(edit.selStart, edit.selEnd))
                            viewModel.onBodyChange(edit.text)
                            viewModel.recordHistory()
                            haptics.confirm()
                        } else if (redactMode && newTfv.text == bodyTfv.text) {
                            // Pure tap/drag (no typing) while redacting → mark the target; effect applies it.
                            bodyTfv = newTfv
                            pendingRedact = newTfv.selection
                        } else {
                            // Auto-continue lists: a single just-typed newline after a list/task/
                            // quote item inserts the next prefix (Enter on an empty item ends it).
                            val continued = if (
                                newTfv.text.length == bodyTfv.text.length + 1 &&
                                newTfv.selection.collapsed &&
                                newTfv.selection.start > 0 &&
                                newTfv.text.getOrNull(newTfv.selection.start - 1) == '\n'
                            ) MarkdownEdit.continueList(newTfv.text, newTfv.selection.start) else null
                            if (continued != null) {
                                bodyTfv = TextFieldValue(continued.text, selection = TextRange(continued.selStart))
                                viewModel.onBodyChange(continued.text)
                            } else {
                                bodyTfv = newTfv
                                viewModel.onBodyChange(newTfv.text)
                            }
                            if (redactMode) pendingRedact = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp)
                        .focusRequester(bodyFocusRequester)
                        .onFocusChanged {
                            bodyFocused = it.isFocused
                            if (!it.isFocused) editRequested = false // back to reading mode
                        },
                    placeholder = { Text("Start writing… use #tags, [[links]] and !links. Select text → Redact to hide it from AI.") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = liveFont.sp),
                    visualTransformation = bodyTransform,
                    colors = transparentColors,
                )
            }

            // Embedded images (Obsidian-style ![[image.png]]) shown as thumbnails.
            val imageNames = remember(bodyTfv.text) {
                WikiLinks.refs(bodyTfv.text)
                    .filter { it.embed && viewModel.isImageName(it.name) }
                    .map { it.name }
                    .distinct()
            }
            if (imageNames.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    imageNames.forEach { name ->
                        AsyncImage(
                            model = viewModel.imageFile(name),
                            contentDescription = name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(110.dp).clip(RoundedCornerShape(10.dp)),
                        )
                    }
                }
            }

            // Inline note-suggestion menu while typing `!…` or `[[…` (the "Open link" affordance
            // is pinned above the keyboard instead — see the bottom bar below).
            AnimatedVisibility(visible = (bareActive != null || bracketActive != null) && linkSuggestions.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    linkSuggestions.forEach { title ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val bracket = bracketActive
                                    val start = bracket?.first ?: bareActive!!.first
                                    val before = bodyTfv.text.substring(0, start)
                                    var after = bodyTfv.text.substring(cursor.coerceAtMost(bodyTfv.text.length))
                                    // Completing inside an already-closed `[[…]]` must not leave
                                    // the old closing brackets behind.
                                    if (bracket != null && after.startsWith("]]")) after = after.substring(2)
                                    val insert = if (bracket != null) "[[$title]] " else "!$title "
                                    val newText = before + insert + after
                                    bodyTfv = TextFieldValue(newText, selection = TextRange((before + insert).length))
                                    viewModel.onBodyChange(newText)
                                    haptics.tick()
                                }
                                .padding(horizontal = 4.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                if (bracketActive != null) "[[" else "!",
                                style = MaterialTheme.typography.titleMedium,
                                color = accent,
                            )
                            Text(title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Keeps the last lines clear of the above-keyboard accessory bar while editing.
            Spacer(Modifier.height(if (bodyFocused) 64.dp else 8.dp))
        }

            // AI compose/edit lives in a tab that drops down from the top of the editor. It now
            // also shows answers (questions the AI chose not to write into the note).
            if (!focusMode) AiDropdown(
                busy = state.aiComposing,
                answer = state.aiAnswer,
                recent = recentAiCommands,
                commands = slashCommands,
                onSend = viewModel::aiAssist,
                onRemoveRecent = viewModel::removeRecentCommand,
                onDismissAnswer = viewModel::dismissAnswer,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            // Outline popup, toggled from the toolbar's Toc button — tap a heading to jump there.
            // The scroll target is proportional (heading's char offset within the body), which
            // lands close enough for navigation without measuring the laid-out text.
            androidx.compose.animation.AnimatedVisibility(
                visible = outlineOpen && !focusMode,
                modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 4.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp,
                ) {
                    Column(
                        Modifier
                            .width(260.dp)
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                    ) {
                        Text(
                            "Outline",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                        outline.forEach { h ->
                            Text(
                                h.text.ifBlank { "(untitled)" },
                                style = if (h.level == 1) MaterialTheme.typography.titleSmall
                                else MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        outlineOpen = false
                                        haptics.tick()
                                        val len = state.body.length
                                        val target = if (len == 0) 0
                                        else (pageScroll.maxValue.toFloat() * h.offset / len).toInt()
                                        scope.launch { pageScroll.animateScrollTo(target) }
                                    }
                                    .padding(
                                        start = (12 + (h.level - 1) * 14).dp,
                                        end = 12.dp,
                                        top = 8.dp,
                                        bottom = 8.dp,
                                    ),
                            )
                        }
                    }
                }
            }

            // Font-size popup, toggled from the toolbar's "Aa" button — drag to resize text live.
            // (Fully qualified: inside this Box the outer Column's scope would otherwise win
            // overload resolution and DslMarker rejects its implicit receiver.)
            androidx.compose.animation.AnimatedVisibility(
                visible = fontControlOpen,
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 4.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    shadowElevation = 4.dp,
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "Font size  ${liveFont.toInt()}sp",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        androidx.compose.material3.Slider(
                            value = liveFont,
                            onValueChange = { liveFont = it }, // live preview
                            onValueChangeFinished = { viewModel.setFontSp(liveFont) }, // persist
                            valueRange = 10f..32f,
                            modifier = Modifier.width(220.dp),
                        )
                        // Note stats — computed only while this popup is open.
                        val stats = remember(state.body) {
                            val words = Regex("\\S+").findAll(state.body).count()
                            Triple(words, state.body.length, (words + 199) / 200)
                        }
                        Text(
                            "${stats.first} words · ${stats.second} chars · ~${stats.third} min read",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }

            // Formatting accessory bar — floats above the keyboard while editing the body, so the
            // common markdown actions are reachable without scrolling up to the toolbar. Hidden when
            // the "open link" affordance is showing (they share the above-keyboard slot).
            androidx.compose.animation.AnimatedVisibility(
                visible = bodyFocused && openId == null,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding(),
            ) {
                Surface(
                    tonalElevation = 3.dp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // One tap to put the keyboard away (drops back to reading mode).
                        val focusManager = LocalFocusManager.current
                        ToolButton(Icons.Outlined.KeyboardHide, "Done — hide keyboard") { focusManager.clearFocus() }
                        ToolDivider()
                        // Selection actions appear at the front whenever text is selected.
                        if (!bodyTfv.selection.collapsed) {
                            val sel = bodyTfv.text.substring(
                                bodyTfv.selection.min.coerceIn(0, bodyTfv.text.length),
                                bodyTfv.selection.max.coerceIn(0, bodyTfv.text.length),
                            )
                            ToolButton(Icons.Outlined.AutoAwesome, "Ask AI about selection") { viewModel.askAboutSelection(sel) }
                            ToolButton(Icons.Outlined.Style, "Make cards from selection") { viewModel.cardsFromSelection(sel) }
                            ToolButton(Icons.Outlined.ContentCut, "Cut selection") { cutSelection() }
                            ToolButton(Icons.Outlined.ContentCopy, "Copy selection") { copySelectionOrAll() }
                            ToolDivider()
                        }
                        // Same live ON/OFF states as the pinned toolbar, within thumb reach.
                        ToolToggle(Icons.Outlined.FormatBold, "Bold", checked = fmt.bold) { applyFormat(EditorFormat.BOLD) }
                        ToolToggle(Icons.Outlined.FormatItalic, "Italic", checked = fmt.italic) { applyFormat(EditorFormat.ITALIC) }
                        ToolToggle(Icons.Outlined.FormatStrikethrough, "Strikethrough", checked = fmt.strike) { applyFormat(EditorFormat.STRIKE) }
                        ToolToggle(Icons.Outlined.Code, "Code", checked = fmt.code) { applyFormat(EditorFormat.CODE) }
                        ToolToggle(Icons.Outlined.Title, "Heading", checked = fmt.heading) { applyFormat(EditorFormat.HEADING) }
                        ToolToggle(Icons.Outlined.FormatListBulleted, "Bullet list", checked = fmt.bullet) { applyFormat(EditorFormat.BULLET) }
                        ToolToggle(Icons.Outlined.CheckBox, "Checkbox", checked = fmt.checkbox) { applyFormat(EditorFormat.CHECKBOX) }
                        ToolToggle(Icons.Outlined.FormatQuote, "Quote", checked = fmt.quote) { applyFormat(EditorFormat.QUOTE) }
                        ToolButton(Icons.Outlined.ContentPaste, "Paste", enabled = clipboard.hasText()) { pasteAtCursor() }
                        ToolToggle(Icons.Outlined.VisibilityOff, "Redact mode", checked = redactMode) {
                            redactMode = !redactMode; pendingRedact = null
                        }
                    }
                }
            }

            // Obsidian-style view toggle — live preview (default, still editable) ⇄ raw source.
            // Always on screen (bottom-right, above the bars); hidden in focus mode, where the
            // only chrome is the exit button.
            if (!focusMode) androidx.compose.material3.SmallFloatingActionButton(
                onClick = { sourceMode = !sourceMode },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .imePadding()
                    .padding(end = 14.dp, bottom = 72.dp),
            ) {
                Icon(
                    if (sourceMode) Icons.Outlined.MenuBook else Icons.Outlined.Code,
                    contentDescription = if (sourceMode) "Live preview" else "View raw markdown",
                )
            }

            // Pinned "open link" affordance — floats just above the on-screen keyboard whenever the
            // cursor rests inside a resolved [[link]]/!link.
            androidx.compose.animation.AnimatedVisibility(
                visible = openId != null,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { openId?.let { viewModel.save(); onOpenNote(it) } }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "Open \"${openTitle.orEmpty()}\"",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            // The one piece of chrome in focus mode: a quiet exit button (back also exits).
            if (focusMode) {
                IconButton(
                    onClick = { focusMode = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 6.dp, top = 2.dp),
                ) {
                    Icon(
                        Icons.Outlined.FullscreenExit,
                        contentDescription = "Exit focus mode",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        }
    }

    // Trash is recoverable (Notes → trash view), but confirm anyway — it's a whole note.
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Move to trash?") },
            text = {
                Text(
                    "\"${state.title.ifBlank { "Untitled" }}\" will move to the trash in Notes, " +
                        "where it can be restored.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.deleteNote { onBack() } }) {
                    Text("Move to trash")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }

    // Version history — restore an earlier body (the restore itself is undoable with ↶).
    if (versionsOpen) {
        VersionHistoryDialog(
            versions = versions,
            onRestore = { v ->
                viewModel.restoreVersion(v)
                versionsOpen = false
            },
            onDismiss = { versionsOpen = false },
        )
    }

    AiResultSheet(state.ai, onDismiss = viewModel::dismissAi)

    // Full-screen colour-coded analysis overlay (client-only; offers save-as-copy / overwrite).
    AnalysisOverlay(
        state = state.analysis,
        body = state.body,
        fontSp = liveFont,
        imageFile = viewModel::imageFile,
        isImageName = viewModel::isImageName,
        onSaveCopy = viewModel::saveAnnotatedCopy,
        onOverwrite = viewModel::overwriteWithAnnotated,
        onDismiss = viewModel::dismissAnalysis,
    )
}

/**
 * The AI compose/edit control, presented as a tab that drops down from the top of the editor.
 * A little handle hangs from the top — pull it down (or tap) to open the prompt; the rewrite
 * streams straight into the body below, so the note visibly updates in real time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiDropdown(
    busy: Boolean,
    answer: String?,
    recent: List<String>,
    commands: List<com.generic.mnemos.core.commands.CommandNote.CommandDoc>,
    onSend: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onDismissAnswer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    // TextFieldValue so palette picks can put the cursor AFTER the inserted command.
    var text by remember { mutableStateOf(TextFieldValue("")) }
    // Keep the panel open so the user watches an edit stream in; an answer also pops it open.
    val send = { if (text.text.isNotBlank() && !busy) { onSend(text.text); text = TextFieldValue("") } }
    androidx.compose.runtime.LaunchedEffect(answer) { if (answer != null) open = true }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(visible = open) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Ask AI to write, edit, or ask a question about this note",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Recent commands — tap to re-run, ✕ to forget. Appears once you've sent something.
                    if (recent.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            recent.forEach { cmd ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            cmd.take(28),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            maxLines = 1,
                                            modifier = Modifier
                                                .clickable { if (!busy) onSend(cmd) }
                                                .padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                                        )
                                        Icon(
                                            Icons.Outlined.Close,
                                            contentDescription = "Remove \"${cmd.take(20)}\"",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .clickable { onRemoveRecent(cmd) }
                                                .padding(start = 2.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
                                                .size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Typing "/" opens the command palette — pick a command instead of memorising it.
                    if (text.text.startsWith("/")) {
                        com.generic.mnemos.ui.components.CommandPaletteRow(
                            commands = com.generic.mnemos.core.commands.CommandNote.filter(commands, text.text),
                            onPick = { text = TextFieldValue(it, selection = TextRange(it.length)) },
                        )
                    }

                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Edit: \"add examples\"  ·  Ask: \"what does this mean?\"") },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                        trailingIcon = {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                if (busy) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                } else {
                                    IconButton(onClick = send) {
                                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send to AI")
                                    }
                                }
                            }
                        },
                    )

                    // Response box: shown when the AI answered a question instead of editing the
                    // note. The text lives here only — it is never written into the note body.
                    AnimatedVisibility(visible = answer != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "AI response",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        "Dismiss",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.clickable { onDismissAnswer() },
                                    )
                                }
                                Text(
                                    answer.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }

        // The pull-tab/handle — always visible, hangs from the top.
        Surface(
            shape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount > 4f) open = true else if (dragAmount < -4f) open = false
                    }
                }
                .clickable { open = !open },
        ) {
            Row(
                Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    if (busy) "Writing…" else "AI",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Icon(
                    if (open) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (open) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * The note's tags, pinned under the title: a compact add box plus the current tags as
 * highlighted chips (✕ removes). Commits on Done, or as soon as a space/comma is typed.
 * Works on the same space-separated "#a #b" tagsText the rest of the app already uses.
 * [frontmatterTags] (`tags:` properties parsed from the note text) display as muted chips
 * without a ✕ — they live in the body, so they're edited there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsRow(
    tagsText: String,
    onTagsChange: (String) -> Unit,
    frontmatterTags: List<String> = emptyList(),
) {
    val tags = remember(tagsText) {
        tagsText.split(',', ' ', '\n')
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotBlank() }
            .distinct()
    }
    // Frontmatter tags that aren't already explicit chips (case-insensitive dedupe).
    val fmOnly = remember(tags, frontmatterTags) {
        frontmatterTags.filter { fm -> tags.none { it.equals(fm, ignoreCase = true) } }
    }
    var newTag by remember { mutableStateOf("") }
    fun commit() {
        val t = newTag.trim().removePrefix("#").replace(Regex("\\s+"), "-")
        if (t.isNotBlank() && tags.none { it.equals(t, ignoreCase = true) }) {
            onTagsChange((tags + t).joinToString(" ") { "#$it" })
        }
        newTag = ""
    }
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // The add box — a slim pill, not a full-height text field.
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Outlined.Add,
                    contentDescription = "Add tag",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BasicTextField(
                    value = newTag,
                    onValueChange = { v ->
                        // Typing a space or comma commits the tag immediately.
                        if (v.endsWith(' ') || v.endsWith(',')) { newTag = v.dropLast(1); commit() } else newTag = v
                    },
                    modifier = Modifier.width(88.dp),
                    textStyle = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    decorationBox = { inner ->
                        Box {
                            if (newTag.isEmpty()) {
                                Text(
                                    "Add tag",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
        }
        // The tags themselves — highlighted, next to the box.
        tags.forEach { tag ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "#$tag",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = "Remove #$tag",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onTagsChange(tags.filterNot { it == tag }.joinToString(" ") { "#$it" })
                            }
                            .size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
        }
        // Frontmatter `tags:` properties — part of the note text, shown muted, no ✕.
        fmOnly.forEach { tag ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            ) {
                Text(
                    "#$tag",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * Explain-by-area: the note body rendered as plain raw text with a drag surface on top. Dragging
 * a rectangle highlights every line it touches; releasing sends that text to [onSelected].
 * Rendered raw (no visual transformation) so text-layout offsets ARE body offsets — the mapping
 * from screen rectangle to text needs no transformation bookkeeping.
 */
@Composable
private fun ExplainSelectArea(
    body: String,
    fontSp: Float,
    onSelected: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)

    /** The body text covered by the current drag rectangle (whole lines), or null. */
    fun coveredText(): String? {
        val l = layout ?: return null
        val s = dragStart ?: return null
        val e = dragEnd ?: return null
        val maxY = l.size.height.toFloat()
        val first = l.getLineForVerticalPosition(minOf(s.y, e.y).coerceIn(0f, maxY))
        val last = l.getLineForVerticalPosition(maxOf(s.y, e.y).coerceIn(0f, maxY))
        val from = l.getLineStart(first)
        val to = l.getLineEnd(last, visibleEnd = true)
        if (to <= from) return null
        return Redaction.stripMarkers(body.substring(from, to)).trim().takeIf { it.isNotBlank() }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Drag over the part you want explained",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Cancel",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onCancel() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 320.dp)) {
            Text(
                body,
                fontSize = fontSp.sp,
                color = MaterialTheme.colorScheme.onSurface,
                onTextLayout = { layout = it },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            Canvas(
                Modifier
                    .matchParentSize()
                    .pointerInput(body) {
                        detectDragGestures(
                            onDragStart = { pos -> dragStart = pos; dragEnd = pos },
                            onDrag = { change, _ -> dragEnd = change.position; change.consume() },
                            onDragEnd = {
                                coveredText()?.let(onSelected)
                                dragStart = null; dragEnd = null
                            },
                            onDragCancel = { dragStart = null; dragEnd = null },
                        )
                    },
            ) {
                val l = layout ?: return@Canvas
                val s = dragStart ?: return@Canvas
                val e = dragEnd ?: return@Canvas
                val maxY = l.size.height.toFloat()
                val first = l.getLineForVerticalPosition(minOf(s.y, e.y).coerceIn(0f, maxY))
                val last = l.getLineForVerticalPosition(maxOf(s.y, e.y).coerceIn(0f, maxY))
                for (line in first..last) {
                    val top = l.getLineTop(line)
                    drawRect(
                        color = highlight,
                        topLeft = Offset(l.getLineLeft(line), top),
                        size = Size(
                            (l.getLineRight(line) - l.getLineLeft(line)).coerceAtLeast(24f),
                            l.getLineBottom(line) - top,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Full-screen overlay showing the AI's colour-coded analysis of the note: the body with category
 * highlights, then a glossary of one-line annotations. Nothing here is written to the note unless
 * the user taps "Save as copy" (new note) or "Overwrite" (replace this note's body).
 */
@Composable
private fun AnalysisOverlay(
    state: AnalysisState,
    body: String,
    fontSp: Float,
    imageFile: (String) -> java.io.File,
    isImageName: (String) -> Boolean,
    onSaveCopy: () -> Unit,
    onOverwrite: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state is AnalysisState.Idle) return
    BackHandler(enabled = true) { onDismiss() }
    // Same Obsidian-style render toggle as the editor — flips the Original pane between raw
    // markdown and rendered reading view. (The Analysis pane stays raw: its colour highlights
    // are anchored to raw-text offsets.)
    var renderMd by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Note analysis", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { renderMd = !renderMd }) {
                    Icon(
                        if (renderMd) Icons.Outlined.Edit else Icons.Outlined.MenuBook,
                        contentDescription = if (renderMd) "Show raw markdown" else "Render markdown",
                        tint = if (renderMd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Close analysis")
                }
            }

            when (state) {
                is AnalysisState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        Text("Analysing note…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is AnalysisState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                is AnalysisState.Ready -> {
                    val anns = state.annotations
                    // Build the body with a coloured background behind each annotated excerpt.
                    val highlighted = remember(body, anns) {
                        buildAnnotatedString {
                            append(body)
                            anns.forEach { a ->
                                val idx = body.indexOf(a.excerpt, ignoreCase = true)
                                if (idx >= 0) {
                                    addStyle(
                                        SpanStyle(background = annotationColor(a.category).copy(alpha = 0.32f)),
                                        idx, idx + a.excerpt.length,
                                    )
                                }
                            }
                        }
                    }
                    // Before / after split — original note on the left, the analysis on the right.
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Column(
                            Modifier.weight(1f).fillMaxHeight()
                                .verticalScroll(rememberScrollState()).padding(end = 6.dp),
                        ) {
                            Text(
                                "Original",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                            if (renderMd) {
                                MarkdownView(
                                    body = body,
                                    fontSp = fontSp,
                                    imageFile = imageFile,
                                    isImageName = isImageName,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Text(body, style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSp.sp))
                            }
                        }
                        VerticalDivider()
                        Column(
                            Modifier.weight(1f).fillMaxHeight()
                                .verticalScroll(rememberScrollState()).padding(start = 6.dp),
                        ) {
                            Text(
                                "Analysis",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                            Text(highlighted, style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSp.sp))
                            Spacer(Modifier.size(16.dp))
                            Text("Glossary", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                            anns.forEach { a ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .padding(top = 5.dp)
                                            .size(12.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(annotationColor(a.category)),
                                    )
                                    Column {
                                        Text(
                                            "${a.category} · “${a.excerpt}”",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                        Text(a.comment, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                            Spacer(Modifier.size(12.dp))
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(onClick = onOverwrite, modifier = Modifier.weight(1f)) { Text("Overwrite") }
                        Button(onClick = onSaveCopy, modifier = Modifier.weight(1f)) { Text("Save as copy") }
                    }
                }
                AnalysisState.Idle -> Unit
            }
        }
    }
}

/** Fixed (theme-independent) highlight colour per annotation category. */
private fun annotationColor(category: String): Color = when (category.lowercase()) {
    "definition" -> Color(0xFF4F9DDE)   // blue
    "example" -> Color(0xFF49B675)      // green
    "key-point", "key", "keypoint" -> Color(0xFFE0A12E) // amber
    "simplify" -> Color(0xFFB05CD6)     // purple
    "caveat" -> Color(0xFFD9534F)       // red
    "question" -> Color(0xFFEA7CC0)     // pink
    "context" -> Color(0xFF5BC0C7)      // teal
    else -> Color(0xFF8A8A8A)           // grey
}

/**
 * One transform for the body field: colours `[[wiki-links]]` / `!links`, and renders redaction
 * markers (⟦…⟧) invisibly while greying the text between them. Because the markers are removed
 * from the displayed text, it carries an [OffsetMapping] so the cursor/selection stay correct.
 */
private class EditorVisualTransformation(
    private val titles: List<String>,
    private val linkColor: Color,
    private val redactBg: Color,
) : VisualTransformation {
    // filter() re-runs on recompositions where the text hasn't changed (caret moves, unrelated
    // state); the parse scans the body against every title, so cache the last result. UI-thread only.
    private var lastRaw: String? = null
    private var lastResult: TransformedText? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        lastResult?.let { cached -> if (raw == lastRaw) return cached }
        val linkSpans = WikiLinks.refs(raw).map { it.start to it.end } +
            WikiLinks.bareRefs(raw, titles).map { it.start to it.end }

        val sb = StringBuilder(raw.length)
        val origToTrans = IntArray(raw.length + 1)
        val transToOrig = ArrayList<Int>(raw.length + 1)
        val redactRanges = ArrayList<IntRange>() // in transformed coords
        var redStart = -1
        var t = 0
        for (i in raw.indices) {
            origToTrans[i] = t
            when (raw[i]) {
                Redaction.OPEN -> redStart = t
                Redaction.CLOSE -> {
                    if (redStart in 0..t) { if (t > redStart) redactRanges.add(redStart until t); redStart = -1 }
                }
                else -> { sb.append(raw[i]); transToOrig.add(i); t++ }
            }
        }
        origToTrans[raw.length] = t
        transToOrig.add(raw.length)

        val ann = AnnotatedString.Builder(sb.toString())
        val linkStyle = SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)
        linkSpans.forEach { (s, e) ->
            val ts = origToTrans[s.coerceIn(0, raw.length)]
            val te = origToTrans[e.coerceIn(0, raw.length)]
            if (te > ts) ann.addStyle(linkStyle, ts, te)
        }
        redactRanges.forEach { r -> ann.addStyle(SpanStyle(background = redactBg), r.first, r.last + 1) }

        // Heading lines carry their level's colour in raw source view too (markers included) —
        // same tints as live preview and reading mode, so structure reads identically everywhere.
        var ls = 0
        while (ls <= raw.length) {
            val le = raw.indexOf('\n', ls).let { if (it < 0) raw.length else it }
            val trimmed = raw.substring(ls, le).trimStart()
            val level = when {
                trimmed.startsWith("#### ") -> 4
                trimmed.startsWith("### ") -> 3
                trimmed.startsWith("## ") -> 2
                trimmed.startsWith("# ") -> 1
                else -> 0
            }
            if (level > 0) {
                val hs = origToTrans[ls]
                val he = origToTrans[le]
                if (he > hs) ann.addStyle(SpanStyle(color = headingColor(level), fontWeight = FontWeight.Bold), hs, he)
            }
            if (le == raw.length) break
            ls = le + 1
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = origToTrans[offset.coerceIn(0, raw.length)]
            override fun transformedToOriginal(offset: Int): Int = transToOrig[offset.coerceIn(0, transToOrig.size - 1)]
        }
        val result = TransformedText(ann.toAnnotatedString(), mapping)
        lastRaw = raw
        lastResult = result
        return result
    }
}

/** "just now / 5m ago / 2h ago / 3d ago" for version timestamps. */
private fun versionAge(thenMs: Long): String {
    val mins = ((System.currentTimeMillis() - thenMs) / 60_000L).coerceAtLeast(0)
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 60 * 24 -> "${mins / 60}h ago"
        else -> "${mins / (60 * 24)}d ago"
    }
}

/**
 * The note's saved snapshots, newest first — written automatically on material changes (see
 * NotesRepositoryImpl), so a bad AI rewrite, bulk edit, or accidental wipe is always recoverable
 * even after undo history is gone.
 */
@Composable
private fun VersionHistoryDialog(
    versions: List<com.generic.mnemos.core.notes.NoteVersion>,
    onRestore: (com.generic.mnemos.core.notes.NoteVersion) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Version history") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                versions.forEach { v ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${versionAge(v.createdAtMs)} · ${v.body.length} chars",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Restore",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onRestore(v) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                        Text(
                            v.body.replace('\n', ' ').trim().take(140),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

/**
 * A slim scrollbar hugging the right edge of a [verticalScroll] container. Must sit BEFORE
 * verticalScroll in the modifier chain so it draws in viewport space (fixed while the content
 * moves). The thumb is sized by the viewport/content ratio. State is read in the DRAW phase
 * only, so scrolling redraws the bar without recomposing anything.
 */
private fun Modifier.verticalScrollbar(state: androidx.compose.foundation.ScrollState, color: Color): Modifier =
    drawWithContent {
        drawContent()
        // maxValue is Int.MAX_VALUE before the first measure — draw nothing until it's real.
        if (state.maxValue in 1 until Int.MAX_VALUE) {
            val viewport = size.height
            val content = viewport + state.maxValue
            val thumbH = (viewport / content * viewport).coerceAtLeast(48.dp.toPx())
            val y = (state.value.toFloat() / state.maxValue) * (viewport - thumbH)
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width - 4.dp.toPx(), y),
                size = Size(3.dp.toPx(), thumbH),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }

/** Zero-based line index of [offset] in [text]. */
private fun lineIndexOf(text: String, offset: Int): Int =
    text.take(offset.coerceIn(0, text.length)).count { it == '\n' }

/** True when [offset] sits on the rendered checkbox glyph of a `- [ ]` / `- [x]` task line. */
private fun caretOnTaskBox(text: String, offset: Int): Boolean {
    val pos = offset.coerceIn(0, text.length)
    val ls = text.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
    val le = text.indexOf('\n', ls).let { if (it < 0) text.length else it }
    val line = text.substring(ls, le)
    val trimmed = line.trimStart()
    val isTask = trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ")
    if (!isTask) return false
    val contentStart = ls + (line.length - trimmed.length)
    return pos - contentStart <= 1 // on the box glyph or just after it
}

/** Char offset within the [taskIndex]-th task line of [body] (document order), or -1. */
private fun taskLineOffset(body: String, taskIndex: Int): Int {
    var idx = 0
    var pos = 0
    body.split('\n').forEach { line ->
        val trimmed = line.trimStart()
        if (trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ")) {
            if (idx == taskIndex) return pos + (line.length - trimmed.length)
            idx++
        }
        pos += line.length + 1
    }
    return -1
}

/**
 * Obsidian-style LIVE PREVIEW — the editor's default view. Every line renders its markdown in
 * place (markers hidden, styles applied). Because it's a [VisualTransformation] over the real
 * body, the note remains fully editable in rendered form; the offset mapping keeps the caret
 * honest across hidden markers. Redaction ⟦…⟧ markers are hidden on every line (matching
 * [EditorVisualTransformation]), and [[wiki]]/!links keep their accent colour in both views.
 *
 * IMPORTANT: this transformation is a pure function of the text — it MUST NOT depend on the
 * selection or focus. A selection-dependent transform replaces the visual text mid-gesture,
 * which breaks selection handles and the IME's composing session (the old "can't select or
 * type" bug). Editing raw markers is what the source-mode FAB is for.
 *
 * Covered: # … #### headings (marker hidden, text scaled + level-coloured), `- ` bullets
 * (drawn •), task boxes (`- [ ] ` → ☐, `- [x] ` → ☑ + struck), `> ` quotes (italic), --- rules
 * + ``` fences (dimmed), inline **bold** / *italic* / ~~strike~~ / `code` / [[link]] with
 * markers hidden. Unclosed inline markers style to the end of their line, mirroring
 * [Markdown.inlineSegments].
 */
private class LiveMarkdownTransformation(
    private val titles: List<String>,
    private val linkColor: Color,
    private val redactBg: Color,
    private val codeBg: Color,
    private val quoteColor: Color,
    private val dimColor: Color,
    private val fontSp: Float,
) : VisualTransformation {

    private data class SpanReq(val start: Int, val end: Int, val style: SpanStyle)

    // Same memoisation as EditorVisualTransformation: the render pass is O(body × titles) and
    // filter() is re-invoked on selection-only recompositions. UI-thread only.
    private var lastRaw: String? = null
    private var lastResult: TransformedText? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        lastResult?.let { cached -> if (raw == lastRaw) return cached }
        val sb = StringBuilder(raw.length)
        val origToTrans = IntArray(raw.length + 1)
        val transToOrig = ArrayList<Int>(raw.length + 1)
        val spans = ArrayList<SpanReq>() // transformed coords
        var redStart = -1 // transformed start of an open ⟦…⟧ redaction

        fun keep(i: Int) { origToTrans[i] = sb.length; sb.append(raw[i]); transToOrig.add(i) }
        fun sub(i: Int, c: Char) { origToTrans[i] = sb.length; sb.append(c); transToOrig.add(i) }
        fun hide(i: Int) { origToTrans[i] = sb.length }
        // Redaction markers vanish on every line; the hidden span gets a grey background.
        fun redactionChar(i: Int): Boolean = when (raw[i]) {
            Redaction.OPEN -> { hide(i); redStart = sb.length; true }
            Redaction.CLOSE -> {
                hide(i)
                if (redStart in 0..sb.length && sb.length > redStart) {
                    spans.add(SpanReq(redStart, sb.length, SpanStyle(background = redactBg)))
                }
                redStart = -1
                true
            }
            else -> false
        }

        val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)
        val italicStyle = SpanStyle(fontStyle = FontStyle.Italic)
        val strikeStyle = SpanStyle(textDecoration = TextDecoration.LineThrough)
        val codeStyle = SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)
        val linkStyle = SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)
        val dimStyle = SpanStyle(color = dimColor)
        // Level colour + size: the line's colour says WHICH heading it is, not just how big.
        fun headingStyle(level: Int) = SpanStyle(
            fontWeight = FontWeight.Bold,
            color = headingColor(level),
            fontSize = (fontSp * when (level) { 1 -> 1.55f; 2 -> 1.3f; 3 -> 1.15f; else -> 1.08f }).sp,
        )

        fun renderLine(ls: Int, le: Int) {
            val line = raw.substring(ls, le)
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length
            var j = ls
            while (j < ls + indent) { keep(j); j++ }
            var restStyle: SpanStyle? = null
            var lineStruck = false
            // Ordered-list detection: "12. item" → the "12. " renders bold and dimmed.
            val ordDigits = trimmed.takeWhile { it.isDigit() }.length
            val ordered = ordDigits in 1..4 && trimmed.length > ordDigits + 1 &&
                trimmed[ordDigits] == '.' && trimmed[ordDigits + 1] == ' '
            when {
                trimmed.startsWith("#### ") -> { repeat(5) { hide(j + it) }; j += 5; restStyle = headingStyle(4) }
                trimmed.startsWith("### ") -> { repeat(4) { hide(j + it) }; j += 4; restStyle = headingStyle(3) }
                trimmed.startsWith("## ") -> { repeat(3) { hide(j + it) }; j += 3; restStyle = headingStyle(2) }
                trimmed.startsWith("# ") -> { repeat(2) { hide(j + it) }; j += 2; restStyle = headingStyle(1) }
                trimmed.startsWith("- [ ] ") -> { sub(j, '☐'); keep(j + 1); repeat(4) { hide(j + 2 + it) }; j += 6 }
                trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> {
                    sub(j, '☑'); keep(j + 1); repeat(4) { hide(j + 2 + it) }; j += 6
                    restStyle = dimStyle
                    lineStruck = true
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                    sub(j, '•'); keep(j + 1); j += 2
                }
                ordered -> {
                    val ns = sb.length
                    repeat(ordDigits + 2) { keep(j + it) }
                    j += ordDigits + 2
                    spans.add(SpanReq(ns, sb.length, SpanStyle(fontWeight = FontWeight.Bold, color = dimColor)))
                }
                trimmed.startsWith("> ") -> {
                    val qs = sb.length
                    keep(j); keep(j + 1); j += 2
                    spans.add(SpanReq(qs, sb.length, dimStyle))
                    restStyle = SpanStyle(color = quoteColor, fontStyle = FontStyle.Italic)
                }
                trimmed == "---" || trimmed == "***" || trimmed == "___" || trimmed.startsWith("```") -> {
                    val ds = sb.length
                    while (j < le) { if (!redactionChar(j)) keep(j); j++ }
                    if (sb.length > ds) spans.add(SpanReq(ds, sb.length, dimStyle))
                    return
                }
            }
            val restStart = sb.length
            var bold = false; var bStart = 0
            var italic = false; var iStart = 0
            var strike = false; var stStart = 0
            while (j < le) {
                if (redactionChar(j)) { j++; continue }
                val c = raw[j]
                val rest = le - j
                when {
                    c == '`' -> {
                        val end = raw.indexOf('`', j + 1)
                        if (end in (j + 1) until le) {
                            hide(j)
                            val cs = sb.length
                            for (k in j + 1 until end) if (!redactionChar(k)) keep(k)
                            hide(end)
                            if (sb.length > cs) spans.add(SpanReq(cs, sb.length, codeStyle))
                            j = end + 1
                        } else { keep(j); j++ }
                    }
                    rest >= 4 && raw.startsWith("[[", j) -> {
                        val end = raw.indexOf("]]", j + 2)
                        if (end >= j + 2 && end + 1 < le) {
                            hide(j); hide(j + 1)
                            val ks = sb.length
                            for (k in j + 2 until end) if (!redactionChar(k)) keep(k)
                            hide(end); hide(end + 1)
                            if (sb.length > ks) spans.add(SpanReq(ks, sb.length, linkStyle))
                            j = end + 2
                        } else { keep(j); j++ }
                    }
                    rest >= 2 && raw.startsWith("**", j) -> {
                        if (!bold) { bold = true; bStart = sb.length }
                        else { if (sb.length > bStart) spans.add(SpanReq(bStart, sb.length, boldStyle)); bold = false }
                        hide(j); hide(j + 1); j += 2
                    }
                    rest >= 2 && raw.startsWith("~~", j) -> {
                        if (!strike) { strike = true; stStart = sb.length }
                        else { if (sb.length > stStart) spans.add(SpanReq(stStart, sb.length, strikeStyle)); strike = false }
                        hide(j); hide(j + 1); j += 2
                    }
                    c == '*' -> {
                        if (!italic) { italic = true; iStart = sb.length }
                        else { if (sb.length > iStart) spans.add(SpanReq(iStart, sb.length, italicStyle)); italic = false }
                        hide(j); j++
                    }
                    else -> { keep(j); j++ }
                }
            }
            // Unclosed inline markers style to the line's end (mirrors Markdown.inlineSegments).
            if (bold && sb.length > bStart) spans.add(SpanReq(bStart, sb.length, boldStyle))
            if (italic && sb.length > iStart) spans.add(SpanReq(iStart, sb.length, italicStyle))
            if (strike && sb.length > stStart) spans.add(SpanReq(stStart, sb.length, strikeStyle))
            if (lineStruck && sb.length > restStart) spans.add(SpanReq(restStart, sb.length, strikeStyle))
            restStyle?.let { if (sb.length > restStart) spans.add(SpanReq(restStart, sb.length, it)) }
        }

        var i = 0
        while (i <= raw.length) {
            val le = raw.indexOf('\n', i).let { if (it < 0) raw.length else it }
            renderLine(i, le)
            if (le == raw.length) break
            keep(le) // the newline itself
            i = le + 1
        }
        origToTrans[raw.length] = sb.length
        transToOrig.add(raw.length)

        val builder = AnnotatedString.Builder(sb.toString())
        // Wiki/bare-!links keep their accent colour on raw lines too (mapped through the hides).
        val linkSpans = WikiLinks.refs(raw).map { it.start to it.end } +
            WikiLinks.bareRefs(raw, titles).map { it.start to it.end }
        linkSpans.forEach { (s, e) ->
            val ts = origToTrans[s.coerceIn(0, raw.length)]
            val te = origToTrans[e.coerceIn(0, raw.length)]
            if (te > ts) builder.addStyle(linkStyle, ts, te)
        }
        spans.forEach { r -> builder.addStyle(r.style, r.start, r.end) }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = origToTrans[offset.coerceIn(0, raw.length)]
            override fun transformedToOriginal(offset: Int): Int = transToOrig[offset.coerceIn(0, transToOrig.size - 1)]
        }
        val result = TransformedText(builder.toAnnotatedString(), mapping)
        lastRaw = raw
        lastResult = result
        return result
    }
}

/** Cursor-derived link context — remembered so the per-title scans run only when inputs change. */
private data class LinkContext(
    val openTitle: String?,
    val openId: Long?,
    val bareActive: Pair<Int, String>?,
    val bracketActive: Pair<Int, String>?,
    val suggestions: List<String>,
)

/** A markdown formatting action issued by the toolbar / accessory bar. */
enum class EditorFormat { BOLD, ITALIC, STRIKE, CODE, HEADING, BULLET, QUOTE, CHECKBOX, LINK, DIVIDER, CLEAR }

/** Live ON/OFF states for the formatting toggles, derived from the caret's context. */
data class FormatStates(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    val heading: Boolean = false,
    val bullet: Boolean = false,
    val quote: Boolean = false,
    val checkbox: Boolean = false,
)

/**
 * The pinned editor toolbar: small icon buttons that stay at the top of the screen, always
 * accessible. Undo/Redo/Cut/Paste reflect availability; every formatting control is a TOGGLE
 * whose ON state tracks the caret (inside **bold** → Bold lights up). Sections: history |
 * clipboard | inline format | line format | insert/clean | view | AI.
 */
@Composable
private fun EditorToolbar(
    canUndo: Boolean,
    canRedo: Boolean,
    canCut: Boolean,
    canPaste: Boolean,
    fmt: FormatStates,
    redactMode: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    onFormat: (EditorFormat) -> Unit,
    onToggleRedact: () -> Unit,
    onImage: () -> Unit,
    onToggleFont: () -> Unit,
    findOpen: Boolean,
    onToggleFind: () -> Unit,
    hasVersions: Boolean = false,
    onHistory: () -> Unit = {},
    hasOutline: Boolean = false,
    onOutline: () -> Unit = {},
    onFocusMode: () -> Unit = {},
) {
    // Both view modes are editable now (live preview edits in place), so nothing greys out.
    val editable = true
    Surface(tonalElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(Icons.AutoMirrored.Outlined.Undo, "Undo", enabled = canUndo, onClick = onUndo)
            ToolButton(Icons.AutoMirrored.Outlined.Redo, "Redo", enabled = canRedo, onClick = onRedo)
            ToolButton(Icons.Outlined.History, "Version history", enabled = hasVersions, onClick = onHistory)
            ToolDivider()
            // Clipboard: Copy grabs the selection (or the whole note when nothing is selected).
            ToolButton(Icons.Outlined.ContentCopy, "Copy selection or note", onClick = onCopy)
            ToolButton(Icons.Outlined.ContentCut, "Cut selection", enabled = canCut && editable, onClick = onCut)
            ToolButton(Icons.Outlined.ContentPaste, "Paste", enabled = canPaste && editable, onClick = onPaste)
            ToolDivider()
            // Inline formatting — lit while the caret sits inside that formatting.
            ToolToggle(Icons.Outlined.FormatBold, "Bold", checked = fmt.bold, enabled = editable) { onFormat(EditorFormat.BOLD) }
            ToolToggle(Icons.Outlined.FormatItalic, "Italic", checked = fmt.italic, enabled = editable) { onFormat(EditorFormat.ITALIC) }
            ToolToggle(Icons.Outlined.FormatStrikethrough, "Strikethrough", checked = fmt.strike, enabled = editable) { onFormat(EditorFormat.STRIKE) }
            ToolToggle(Icons.Outlined.Code, "Code", checked = fmt.code, enabled = editable) { onFormat(EditorFormat.CODE) }
            ToolDivider()
            // Line formatting — lit while the caret's line carries that prefix.
            ToolToggle(Icons.Outlined.Title, "Heading", checked = fmt.heading, enabled = editable) { onFormat(EditorFormat.HEADING) }
            ToolToggle(Icons.Outlined.FormatListBulleted, "Bullet list", checked = fmt.bullet, enabled = editable) { onFormat(EditorFormat.BULLET) }
            ToolToggle(Icons.Outlined.CheckBox, "Checkbox", checked = fmt.checkbox, enabled = editable) { onFormat(EditorFormat.CHECKBOX) }
            ToolToggle(Icons.Outlined.FormatQuote, "Quote", checked = fmt.quote, enabled = editable) { onFormat(EditorFormat.QUOTE) }
            ToolDivider()
            // Insert / clean up.
            ToolButton(Icons.Outlined.AddLink, "Wrap in [[link]]", enabled = editable) { onFormat(EditorFormat.LINK) }
            ToolButton(Icons.Outlined.HorizontalRule, "Insert divider", enabled = editable) { onFormat(EditorFormat.DIVIDER) }
            ToolButton(Icons.Outlined.FormatClear, "Clear formatting", enabled = editable) { onFormat(EditorFormat.CLEAR) }
            ToolButton(Icons.Outlined.Image, "Insert image", enabled = editable, onClick = onImage)
            ToolDivider()
            ToolToggle(Icons.Outlined.VisibilityOff, "Redact mode", checked = redactMode, enabled = editable, onClick = onToggleRedact)
            ToolToggle(Icons.Outlined.FindReplace, "Find & replace", checked = findOpen, enabled = editable, onClick = onToggleFind)
            ToolButton(Icons.AutoMirrored.Outlined.Toc, "Outline — jump to heading", enabled = hasOutline, onClick = onOutline)
            ToolButton(Icons.Outlined.FormatSize, "Font size", onClick = onToggleFont)
            ToolButton(Icons.Outlined.CenterFocusStrong, "Focus mode", onClick = onFocusMode)
        }
    }
}

/**
 * The AI toolbar — its own pinned row, deliberately separate from text formatting: these are the
 * app's headline convenience actions (analyse, summarise, revise), so they get labelled chips and
 * permanent one-tap access instead of hiding at the end of the formatting row.
 */
@Composable
private fun AiToolbar(
    onAnalyze: () -> Unit,
    onSummarise: () -> Unit,
    onCards: () -> Unit,
    onCloze: () -> Unit,
    onConnections: () -> Unit,
    onExplain: () -> Unit,
) {
    Surface(tonalElevation = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AiChip(Icons.Outlined.Biotech, "Analyze", onAnalyze)
            AiChip(Icons.Outlined.Summarize, "Summarise", onSummarise)
            AiChip(Icons.Outlined.Style, "Cards", onCards)
            AiChip(Icons.Outlined.Hub, "Connect", onConnections)
            AiChip(Icons.Outlined.AutoAwesome, "Explain", onExplain)
            // Cloze sits at the back of the queue — the niche one of the six.
            AiChip(Icons.Outlined.Quiz, "Cloze", onCloze)
        }
    }
}

/** A compact labelled chip for the AI toolbar (icon + verb — clearer than a bare icon). */
@Composable
private fun AiChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { haptics.tick(); onClick() },
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    IconButton(onClick = { haptics.tick(); onClick() }, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ToolToggle(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val haptics = rememberHaptics()
    IconToggleButton(
        checked = checked,
        onCheckedChange = { haptics.tick(); onClick() },
        enabled = enabled,
        modifier = Modifier.size(40.dp),
        colors = IconButtonDefaults.iconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.primary,
            checkedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ToolDivider() {
    VerticalDivider(modifier = Modifier.height(24.dp).padding(horizontal = 4.dp))
}
