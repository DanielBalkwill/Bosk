package app.bosk.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bosk.core.notes.ActiveNoteStore
import app.bosk.core.notes.Note
import app.bosk.core.notes.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Backs the quick switcher: recents when the query is blank, otherwise a ranked mix of
 * title-prefix, title-contains, and FTS body matches.
 */
@HiltViewModel
class QuickSwitcherViewModel @Inject constructor(
    private val notes: NotesRepository,
    private val activeNote: ActiveNoteStore,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    fun setQuery(q: String) { _query.value = q }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val results: StateFlow<List<Note>> =
        _query.debounce(60).mapLatest { q -> search(q) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun search(q: String): List<Note> {
        val all = notes.observeNotes().first()
        if (q.isBlank()) {
            // Recently-focused notes first, then most recently edited.
            val byId = all.associateBy { it.id }
            val recents = activeNote.recent.value.mapNotNull { byId[it] }
            return (recents + all.sortedByDescending { it.updatedAtMs }).distinctBy { it.id }.take(12)
        }
        val prefix = all.filter { it.title.startsWith(q, ignoreCase = true) }
        val contains = all.filter { it.title.contains(q, ignoreCase = true) }
        val fts = runCatching { notes.search(q) }.getOrDefault(emptyList())
        return (prefix + contains + fts).distinctBy { it.id }.take(15)
    }
}

/**
 * The QUICK SWITCHER: a keyboard-first jump-to-note dialog (Obsidian's most-used feature).
 * Opens with recents; typing ranks title-prefix > title-contains > full-text matches; the
 * keyboard's Go action opens the top hit.
 */
@Composable
fun QuickSwitcher(
    onOpenNote: (Long) -> Unit,
    onDismiss: () -> Unit,
    viewModel: QuickSwitcherViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        viewModel.setQuery("") // fresh open → recents
        focusRequester.requestFocus()
    }
    val openTop = {
        results.firstOrNull()?.let { onOpenNote(it.id); onDismiss() } ?: Unit
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    placeholder = { Text("Jump to note…") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { openTop() }),
                )
                if (results.isEmpty() && query.isNotBlank()) {
                    Text(
                        "No matches",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 6.dp)) {
                        items(results, key = { it.id }) { note ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenNote(note.id); onDismiss() }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    note.title.ifBlank { "Untitled" },
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                )
                                val hint = note.body.replace('\n', ' ').trim()
                                if (hint.isNotBlank()) {
                                    Text(
                                        hint.take(90),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
