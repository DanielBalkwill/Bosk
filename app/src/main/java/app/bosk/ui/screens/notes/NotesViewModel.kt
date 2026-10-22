package app.bosk.ui.screens.notes

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bosk.core.ai.BulkEditService
import app.bosk.core.cache.SessionCache
import app.bosk.core.notes.Note
import app.bosk.core.notes.NotesRepository
import app.bosk.core.notes.ObsidianImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repo: NotesRepository,
    private val obsidian: ObsidianImporter,
    private val exporter: app.bosk.core.notes.VaultExporter,
    private val bulk: BulkEditService,
    private val cache: SessionCache,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    /** List vs. side-by-side grid layout for the notes list. */
    private val _gridView = MutableStateFlow(false)
    val gridView: StateFlow<Boolean> = _gridView.asStateFlow()
    fun toggleGridView() { _gridView.value = !_gridView.value }

    /** Bulk-edit progress: non-null while running OR showing the last result line. */
    private val _bulkStatus = MutableStateFlow<String?>(null)
    val bulkStatus: StateFlow<String?> = _bulkStatus.asStateFlow()
    private val _bulkBusy = MutableStateFlow(false)
    val bulkBusy: StateFlow<Boolean> = _bulkBusy.asStateFlow()
    fun dismissBulkStatus() { if (!_bulkBusy.value) _bulkStatus.value = null }

    val notes: StateFlow<List<Note>> =
        combine(repo.observeNotes(), _query) { list, q ->
            if (q.isBlank()) list
            else list.filter { n ->
                n.title.contains(q, true) ||
                    n.body.contains(q, true) ||
                    n.tags.any { it.contains(q, true) }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            // Seed from the warm cache so the list paints instantly on re-entry instead of flashing
            // empty while Room re-emits. (The query is blank at construction, so no filter needed.)
            cache.notes,
        )

    fun setQuery(q: String) { _query.value = q }

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }

    /**
     * Bulk-edit notes from a single free-text request. The heavy lifting (matching, per-note AI
     * edits, undo bracketing, link re-sync) lives in the shared [BulkEditService] so the `/bulk`
     * command behaves identically in the editor dropdown and the terminal.
     */
    fun bulkEdit(request: String) {
        val q = request.trim()
        if (q.isBlank() || _bulkBusy.value) return
        viewModelScope.launch {
            _bulkBusy.value = true
            try {
                _bulkStatus.value = bulk.run(q) { progress -> _bulkStatus.value = progress }
            } catch (e: Exception) {
                _bulkStatus.value = "Bulk edit failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                _bulkBusy.value = false
            }
        }
    }

    /** Import an Obsidian vault folder (picked via SAF) into notes. */
    fun importObsidianVault(treeUri: Uri) {
        viewModelScope.launch {
            _message.value = "Importing vault…"
            _message.value = runCatching { obsidian.import(treeUri) }.fold(
                onSuccess = { count ->
                    if (count == 0) "No .md notes found in that folder."
                    else "Imported $count note${if (count == 1) "" else "s"}. Tap Load graph to draw links."
                },
                // Surface the real reason instead of masking every failure as an empty vault.
                onFailure = { e -> "Import failed: ${e.message ?: e.javaClass.simpleName}" },
            )
        }
    }

    /** Export the whole vault as a Markdown zip (notes + embedded images) to a user-picked file. */
    fun exportVault(target: Uri) {
        viewModelScope.launch {
            _message.value = "Exporting vault…"
            _message.value = runCatching { exporter.exportTo(target) }.fold(
                onSuccess = { count -> "Exported $count note${if (count == 1) "" else "s"} as Markdown." },
                onFailure = { e -> "Export failed: ${e.message ?: e.javaClass.simpleName}" },
            )
        }
    }

}
