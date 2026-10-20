package com.generic.mnemos.core.notes

import com.generic.mnemos.core.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** One queued note in the Compiler, optionally carrying a request to show its summary. */
data class CompilerItem(val noteId: Long, val withSummary: Boolean = true)

/**
 * App-wide "Compiler" queue: a staging area other screens (Notes, the graph's Overview mode, …)
 * push notes into, where the user reviews them — with or without summaries — and decides what to
 * do (turn them into flashcards, or compile them into one synthesis note). The queue is mirrored
 * into DataStore so it survives app restarts.
 */
@Singleton
class CompilerStore @Inject constructor(
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _items = MutableStateFlow<List<CompilerItem>>(emptyList())
    val items: StateFlow<List<CompilerItem>> = _items.asStateFlow()

    init {
        // Restore the persisted queue once on process start (mutations before the load win).
        scope.launch {
            val saved = decode(runCatching { settings.compilerQueue.first() }.getOrDefault(""))
            if (saved.isNotEmpty() && _items.value.isEmpty()) _items.value = saved
        }
    }

    fun add(noteId: Long, withSummary: Boolean = true) {
        if (noteId <= 0) return
        update { list -> if (list.any { it.noteId == noteId }) list else list + CompilerItem(noteId, withSummary) }
    }

    fun addAll(noteIds: Collection<Long>, withSummary: Boolean = true) {
        noteIds.forEach { add(it, withSummary) }
    }

    fun remove(noteId: Long) = update { it.filterNot { item -> item.noteId == noteId } }

    fun toggleSummary(noteId: Long) = update {
        it.map { item -> if (item.noteId == noteId) item.copy(withSummary = !item.withSummary) else item }
    }

    /** Move a queued note one slot up or down (compile order = queue order). */
    fun move(noteId: Long, up: Boolean) = update { list ->
        val i = list.indexOfFirst { it.noteId == noteId }
        val j = if (up) i - 1 else i + 1
        if (i < 0 || j !in list.indices) list
        else list.toMutableList().also { it[i] = it[j].also { other -> it[j] = it[i] } }
    }

    fun clear() = update { emptyList() }

    private fun update(block: (List<CompilerItem>) -> List<CompilerItem>) {
        _items.value = block(_items.value)
        val encoded = encode(_items.value)
        scope.launch { runCatching { settings.setCompilerQueue(encoded) } }
    }

    private fun encode(items: List<CompilerItem>): String =
        items.joinToString(";") { "${it.noteId}:${if (it.withSummary) 1 else 0}" }

    private fun decode(encoded: String): List<CompilerItem> =
        encoded.split(";").mapNotNull { part ->
            val bits = part.split(":")
            val id = bits.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            if (id <= 0) null else CompilerItem(id, bits.getOrNull(1) != "0")
        }
}
