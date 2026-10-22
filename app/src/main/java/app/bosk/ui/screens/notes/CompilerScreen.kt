package app.bosk.ui.screens.notes

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The Compiler: a staging queue of notes (sent from Notes or the graph's Overview mode). Each row
 * shows the note with a brief summary beneath, and an options menu on the right to act on it
 * (open, make flashcards for Practise, toggle/refresh the summary, remove).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompilerScreen(
    onOpenNote: (Long) -> Unit,
    onBackToNotes: () -> Unit = {},
    viewModel: CompilerViewModel = hiltViewModel(),
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.consumeMessage() }
    }
    // A freshly-compiled synthesis note opens straight in the editor.
    LaunchedEffect(Unit) { viewModel.openCompiled.collect { onOpenNote(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compiler (${rows.size})") },
                navigationIcon = {
                    IconButton(onClick = onBackToNotes) {
                        Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Back to notes")
                    }
                },
                actions = {
                    if (working) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    }
                    if (rows.isNotEmpty()) {
                        // THE compile: merge the queue into one AI-synthesised study note.
                        IconButton(onClick = viewModel::compileAll, enabled = !working) {
                            Icon(Icons.Outlined.Merge, contentDescription = "Compile into one note")
                        }
                        IconButton(onClick = viewModel::makeAllFlashcards, enabled = !working) {
                            Icon(Icons.Outlined.Style, contentDescription = "Flashcards for all")
                        }
                        IconButton(onClick = viewModel::clearAll) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear queue")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "Compiler queue is empty.\nSend notes here from Notes or the graph's Overview mode.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rows, key = { it.note.id }, contentType = { "compilerRow" }) { row ->
                    CompilerRowItem(
                        row = row,
                        onOpen = { onOpenNote(row.note.id) },
                        onFlashcards = { viewModel.makeFlashcards(row.note.id) },
                        onToggleSummary = { viewModel.toggleSummary(row.note.id) },
                        onResummarise = { viewModel.resummarise(row.note.id) },
                        onRemove = { viewModel.remove(row.note.id) },
                        onMoveUp = { viewModel.moveUp(row.note.id) },
                        onMoveDown = { viewModel.moveDown(row.note.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompilerRowItem(
    row: CompilerRow,
    onOpen: () -> Unit,
    onFlashcards: () -> Unit,
    onToggleSummary: () -> Unit,
    onResummarise: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.note.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Brief summary beneath the note.
                when {
                    !row.withSummary -> Text(
                        "Summary hidden",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    row.summarising || row.summary == null -> Row(
                        Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        Text("Summarising…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> Text(
                        row.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            // Options menu on the right.
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Open note") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
                        onClick = { menuOpen = false; onOpen() },
                    )
                    DropdownMenuItem(
                        text = { Text("Make flashcards") },
                        leadingIcon = { Icon(Icons.Outlined.Style, contentDescription = null) },
                        onClick = { menuOpen = false; onFlashcards() },
                    )
                    DropdownMenuItem(
                        text = { Text(if (row.withSummary) "Hide summary" else "Show summary") },
                        onClick = { menuOpen = false; onToggleSummary() },
                    )
                    DropdownMenuItem(
                        text = { Text("Refresh summary") },
                        onClick = { menuOpen = false; onResummarise() },
                    )
                    DropdownMenuItem(
                        text = { Text("Move up") },
                        leadingIcon = { Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = null) },
                        onClick = { menuOpen = false; onMoveUp() },
                    )
                    DropdownMenuItem(
                        text = { Text("Move down") },
                        leadingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null) },
                        onClick = { menuOpen = false; onMoveDown() },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove from queue") },
                        onClick = { menuOpen = false; onRemove() },
                    )
                }
            }
        }
    }
}
