package com.generic.mnemos.core.ai

import com.generic.mnemos.core.notes.Note
import com.generic.mnemos.core.notes.NoteEditHistories
import com.generic.mnemos.core.notes.NotesRepository
import com.generic.mnemos.core.notes.WikiLinkSyncer
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies one free-text instruction to MANY notes — the engine behind the Notes tab's bulk bar and
 * the `/bulk` command (editor AI dropdown + terminal). Shared so every surface matches notes,
 * edits, and reports identically.
 *
 * Matching is layered so a reasonable request never dies on a bad AI keyword list:
 *  1. an explicit `#tag` in the request scopes to that tag; a trailing "all" means the whole vault;
 *  2. AI-derived search terms, matched against title/body/tags;
 *  3. FTS search over the terms AND the raw request;
 *  4. the request's own meaningful words as a last resort.
 *
 * Every change is recorded in the note's shared [NoteEditHistories] stack, so the editor
 * toolbar's undo (↶) reverses it note by note, and wiki-link edges are re-synced per note.
 */
@Singleton
class BulkEditService @Inject constructor(
    private val ai: NoteAiService,
    private val notes: NotesRepository,
    private val wikiLinks: WikiLinkSyncer,
    private val histories: NoteEditHistories,
) {

    /**
     * Run [request] against the matching notes, reporting progress lines through [onProgress].
     * Returns the final human-readable result line.
     */
    suspend fun run(request: String, onProgress: suspend (String) -> Unit = {}): String {
        val q = request.trim()
        if (q.isBlank()) return "Nothing to do — describe the edit, e.g. \"fix spelling #biology\"."
        onProgress("Finding relevant notes…")
        val all = notes.observeNotes().first()
        val limit = requestedLimit(q) ?: DEFAULT_CAP
        val candidates = matchNotes(all, q).sortedBy { it.title.lowercase() }.take(limit)
        if (candidates.isEmpty()) return "No matching notes found for that request."

        var done = 0
        for ((i, note) in candidates.withIndex()) {
            onProgress("Editing ${i + 1}/${candidates.size}: ${note.title.ifBlank { "Untitled" }}…")
            val newBody = ai.editNoteBody(note, q).getOrNull()
            if (!newBody.isNullOrBlank() && newBody != note.body) {
                // One clean undo step per note, then persist + re-derive its [[link]] edges.
                histories.recordExternalEdit(note.id, note.body, newBody)
                notes.upsert(note.copy(body = newBody))
                wikiLinks.sync(note.copy(body = newBody))
                done++
            }
        }
        return "Updated $done of ${candidates.size} note${if (candidates.size == 1) "" else "s"} — each undoable (↶) in its editor."
    }

    private suspend fun matchNotes(all: List<Note>, request: String): List<Note> {
        // Explicit scope wins: "#tag" anywhere in the request, or a trailing bare "all".
        val tag = Regex("#([\\w-]+)").find(request)?.groupValues?.getOrNull(1)
        if (tag != null) {
            val tagged = all.filter { n -> n.tags.any { it.equals(tag, ignoreCase = true) } }
            if (tagged.isNotEmpty()) return tagged
        }
        if (Regex("\\ball\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(request)) return all

        val terms = ai.deriveSearchQueries(request).getOrDefault(emptyList())
        val byTerms = if (terms.isEmpty()) emptyList() else all.filter { n -> terms.any { contains(n, it) } }
        if (byTerms.isNotEmpty()) return byTerms

        // FTS pass — catches stemming/prefix matches the naive contains() misses.
        val fts = LinkedHashMap<Long, Note>()
        (terms + request).forEach { t ->
            runCatching { notes.search(t) }.getOrDefault(emptyList()).forEach { fts[it.id] = it }
        }
        if (fts.isNotEmpty()) return fts.values.toList()

        // Last resort: the request's own meaningful words.
        val words = request.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 4 }
        return if (words.isEmpty()) emptyList() else all.filter { n -> words.any { contains(n, it) } }
    }

    private fun contains(n: Note, term: String): Boolean =
        n.title.contains(term, ignoreCase = true) ||
            n.body.contains(term, ignoreCase = true) ||
            n.tags.any { it.contains(term, ignoreCase = true) }

    /** First small integer in the request, treated as "edit at most this many notes". */
    fun requestedLimit(text: String): Int? =
        Regex("\\b(\\d{1,3})\\b").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?.takeIf { it in 1..HARD_CAP }?.coerceAtMost(HARD_CAP)

    companion object {
        /** Default cap when the user doesn't name a number. */
        const val DEFAULT_CAP = 15
        /** Hard ceiling even if the user asks for more. */
        const val HARD_CAP = 60
    }
}
