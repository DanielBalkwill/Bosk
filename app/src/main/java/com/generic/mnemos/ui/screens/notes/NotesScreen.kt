package com.generic.mnemos.ui.screens.notes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFolderUpload
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.generic.mnemos.core.notes.Note
import com.generic.mnemos.ui.components.NoteCard
import com.generic.mnemos.ui.components.SwipeableNoteRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onOpenNote: (Long) -> Unit,
    onCreateNote: () -> Unit,
    onOpenCompiler: () -> Unit = {},
    onOpenAiTutor: () -> Unit = {},
    onOpenNoteInTutor: (Long) -> Unit = {},
    onOpenRecycleBin: () -> Unit = {},
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val gridView by viewModel.gridView.collectAsStateWithLifecycle()
    val bulkStatus by viewModel.bulkStatus.collectAsStateWithLifecycle()
    val bulkBusy by viewModel.bulkBusy.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }
    val vaultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let { viewModel.importObsidianVault(it) } }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { viewModel.exportVault(it) } }

    val dateFmt = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    fun dateText(note: Note): String =
        note.updatedAtMs.takeIf { it > 0 }?.let { dateFmt.format(Date(it)) } ?: "—"

    // Quick switcher — jump straight to a note without scrolling the list.
    var switcherOpen by remember { mutableStateOf(false) }
    if (switcherOpen) {
        com.generic.mnemos.ui.components.QuickSwitcher(
            onOpenNote = onOpenNote,
            onDismiss = { switcherOpen = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notes") },
                actions = {
                    IconButton(onClick = { switcherOpen = true }) {
                        Icon(Icons.Outlined.Bolt, contentDescription = "Quick switcher")
                    }
                    IconButton(onClick = onOpenAiTutor) {
                        Icon(Icons.Outlined.School, contentDescription = "AI Tutor")
                    }
                    IconButton(onClick = onOpenCompiler) {
                        Icon(Icons.Outlined.Dashboard, contentDescription = "Open Compiler")
                    }
                    IconButton(onClick = { viewModel.toggleGridView() }) {
                        Icon(
                            if (gridView) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                            contentDescription = if (gridView) "List view" else "Grid view",
                        )
                    }
                    IconButton(onClick = { vaultLauncher.launch(null) }) {
                        Icon(Icons.Outlined.DriveFolderUpload, contentDescription = "Import Obsidian vault")
                    }
                    IconButton(onClick = { exportLauncher.launch("mnemos-vault.zip") }) {
                        Icon(Icons.Outlined.Archive, contentDescription = "Export vault as Markdown zip")
                    }
                    IconButton(onClick = onOpenRecycleBin) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Recycle bin")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateNote) {
                Icon(Icons.Outlined.Add, contentDescription = "New note")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Bulk AI editor — one request changes many notes (finds them via AI-derived queries).
            BulkAiBar(
                status = bulkStatus,
                busy = bulkBusy,
                onSubmit = viewModel::bulkEdit,
                onDismiss = viewModel::dismissBulkStatus,
            )

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search notes") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
            )

            if (notes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (query.isBlank()) "No notes yet — tap + to add one."
                        else "No matches for \"$query\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (gridView) {
                // Side-by-side note cards with the note's date below each.
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp, top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(notes, key = { it.id }, contentType = { "noteGrid" }) { note ->
                        GridNoteCard(note = note, dateText = dateText(note), onClick = { onOpenNote(note.id) })
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(notes, key = { it.id }, contentType = { "noteRow" }) { note ->
                        // Swipe right → open in the AI Tutor (auto-inquiry); swipe left → reveal an
                        // X that moves the note to the recycle bin.
                        SwipeableNoteRow(
                            note = note,
                            onClick = { onOpenNote(note.id) },
                            onOpenInTutor = { onOpenNoteInTutor(note.id) },
                            onTrash = { viewModel.delete(note.id) },
                        )
                    }
                }
            }
        }
    }
}

/** A compact note card for the side-by-side grid: title, a hint of body, and the date below. */
@Composable
private fun GridNoteCard(note: Note, dateText: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                note.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.body.isNotBlank()) {
                Text(
                    note.body.take(120).replace("\n", " ").trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Text(
                dateText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** AI command bar pinned at the top of Notes that edits MANY notes from one request. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulkAiBar(status: String?, busy: Boolean, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val submit = { if (text.isNotBlank() && !busy) { onSubmit(text); text = "" } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Edit many notes… e.g. \"add a summary line to all biology notes\"") },
            leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = { submit() }),
            trailingIcon = {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    if (busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    else IconButton(onClick = submit) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = "Run bulk edit")
                    }
                }
            },
        )
        if (status != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (busy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    Text(
                        status,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (!busy) {
                        Text(
                            "Dismiss",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onDismiss() }.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
