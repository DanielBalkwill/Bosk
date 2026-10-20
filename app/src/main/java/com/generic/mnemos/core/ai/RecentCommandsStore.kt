package com.generic.mnemos.core.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A small most-recently-used list of the free-text AI instructions the user has sent in the note
 * editor's AI dropdown. Surfaced as quick re-run buttons in that dropdown. In-memory and app-wide
 * (a session concept, shared across notes); newest first, de-duplicated, capped at [MAX].
 */
@Singleton
class RecentCommandsStore @Inject constructor() {
    private val _commands = MutableStateFlow<List<String>>(emptyList())
    val commands: StateFlow<List<String>> = _commands.asStateFlow()

    /** Record a just-sent instruction (ignores blanks and bare slash-only input). */
    fun record(command: String) {
        val c = command.trim()
        if (c.isEmpty() || c == "/") return
        _commands.value = (listOf(c) + _commands.value.filterNot { it.equals(c, ignoreCase = true) }).take(MAX)
    }

    /** Remove one remembered instruction (the ✕ on its chip). */
    fun remove(command: String) {
        _commands.value = _commands.value.filterNot { it.equals(command.trim(), ignoreCase = true) }
    }

    companion object {
        const val MAX = 6
    }
}
