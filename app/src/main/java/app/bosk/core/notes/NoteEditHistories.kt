package app.bosk.core.notes

import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide registry of per-note [EditHistory] stacks, keyed by note id. Previously each editor
 * session owned a private history, so a body change made anywhere ELSE (e.g. the AI terminal's
 * `/empty`) was invisible to the editor's undo/redo. Any surface that mutates a note body should
 * bracket the change through [forNote]'s history — the editor toolbar's ↶/↷ then reverses it.
 *
 * In-memory only (histories are a session concept, like [ActiveNoteStore]). Synchronised because
 * ViewModels and services touch it from different coroutines.
 */
@Singleton
class NoteEditHistories @Inject constructor() {

    private val histories = HashMap<Long, EditHistory>()

    /** The shared history for note [id] (created empty on first ask). */
    @Synchronized
    fun forNote(id: Long): EditHistory = histories.getOrPut(id) { EditHistory() }

    /**
     * Register the history a NEW note was edited under, once its real id is known — so the undo
     * trail of a note's very first session isn't lost when it is next opened.
     */
    @Synchronized
    fun adopt(id: Long, history: EditHistory) {
        if (id > 0) histories.putIfAbsent(id, history)
    }

    /**
     * Record an out-of-editor body change (terminal command, bulk edit, …) as ONE clean undo step:
     * seeds the history with [oldBody] if this note has none yet, then commits [newBody].
     */
    @Synchronized
    fun recordExternalEdit(id: Long, oldBody: String, newBody: String) {
        if (id <= 0) return
        val h = forNote(id)
        if (h.current == null) h.reset(oldBody) else h.commit(oldBody)
        h.commit(newBody)
    }
}
