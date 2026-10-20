package com.generic.mnemos.core.notes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide "which note is in focus" — the single source of truth the Home preview reads and the
 * editor writes. Opening a note in the editor sets it here, so swiping back to Home shows that
 * same note in "Now Reading". On a cold open with nothing set, Home seeds it with a random note.
 *
 * In-memory only (a session concept); it deliberately doesn't persist across process death.
 */
@Singleton
class ActiveNoteStore @Inject constructor() {
    private val _id = MutableStateFlow<Long?>(null)
    /** The focused note id, or null until something is opened / Home seeds a random one. */
    val id: StateFlow<Long?> = _id.asStateFlow()

    private val _recent = MutableStateFlow<List<Long>>(emptyList())
    /** The most-recently focused/edited notes, newest first (capped at [MAX_RECENT]). */
    val recent: StateFlow<List<Long>> = _recent.asStateFlow()

    // A true navigation stack (oldest→newest) of focused notes, so Home's back button can step
    // back through what was previewed. Distinct from [recent] (newest-first & de-duped).
    private val _history = MutableStateFlow<List<Long>>(emptyList())
    /** The focus history (oldest→newest). size ≥ 2 means there's somewhere to go back to. */
    val history: StateFlow<List<Long>> = _history.asStateFlow()

    fun set(id: Long) {
        if (id <= 0) return
        _id.value = id
        _recent.value = (listOf(id) + _recent.value.filterNot { it == id }).take(MAX_RECENT)
        // Push onto the history stack (skip if it's already the top — avoids dupes from re-emits).
        if (_history.value.lastOrNull() != id) {
            _history.value = (_history.value + id).takeLast(MAX_HISTORY)
        }
    }

    /**
     * Step back to the previously previewed note: pop the current entry and focus the prior one.
     * Returns true if it navigated, false if there's nothing to go back to (let the caller handle it).
     */
    fun back(): Boolean {
        val h = _history.value
        if (h.size < 2) return false
        val newHist = h.dropLast(1)
        _history.value = newHist
        val prev = newHist.last()
        _id.value = prev
        _recent.value = (listOf(prev) + _recent.value.filterNot { it == prev }).take(MAX_RECENT)
        return true
    }

    companion object {
        const val MAX_RECENT = 10
        const val MAX_HISTORY = 30
    }
}
