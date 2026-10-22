package app.bosk.core.commands

import app.bosk.core.notes.Note

/**
 * Pure, side-effect-free helpers behind the AI terminal's *real* slash commands (the ones that act on
 * the vault rather than just talking to the AI — see [app.bosk.ui.screens.aiinterface]).
 *
 * Kept here, free of Android/Room/coroutine types, so the parsing + aggregation logic is unit-testable
 * without a device. The ViewModel does the I/O (resolve a note, upsert, …); this object decides *what*.
 */
object VaultActions {

    /**
     * Split a `/tag`/`/untag` argument into `(tag, title)`. The first whitespace-delimited token is the
     * tag (a leading `#` is tolerated and stripped); everything after it is an optional note title
     * (blank → operate on the active note). Returns null when no tag was given.
     *
     * Examples: "biology" → ("biology", ""); "important Mitosis" → ("important", "Mitosis").
     */
    fun parseTagArg(arg: String): Pair<String, String>? {
        val trimmed = arg.trim()
        if (trimmed.isEmpty()) return null
        val tag = trimmed.substringBefore(' ').removePrefix("#").trim()
        if (tag.isEmpty()) return null
        val title = trimmed.removePrefix(trimmed.substringBefore(' ')).trim()
        return tag to title
    }

    /** Add [tag] to a note's tag list (case-insensitive de-dupe, order preserved). */
    fun addTag(existing: List<String>, tag: String): List<String> =
        if (existing.any { it.equals(tag, ignoreCase = true) }) existing else existing + tag

    /** Remove [tag] from a note's tag list (case-insensitive). */
    fun removeTag(existing: List<String>, tag: String): List<String> =
        existing.filterNot { it.equals(tag, ignoreCase = true) }

    /**
     * Every tag used across [notes] with its note count, ordered by count desc then name asc.
     * Tags are compared case-insensitively; the first-seen casing is the one reported.
     */
    fun tagCounts(notes: List<Note>): List<Pair<String, Int>> {
        val canonical = LinkedHashMap<String, String>() // lower → display
        val counts = LinkedHashMap<String, Int>()        // lower → count
        for (note in notes) {
            for (tag in note.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }) {
                val key = tag.lowercase()
                canonical.putIfAbsent(key, tag)
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        return counts.entries
            .map { (key, count) -> canonical.getValue(key) to count }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
    }

    /** A "(copy)" title for a duplicated note. Duplicating a copy chains: "X (copy) (copy)". */
    fun copyTitle(original: String): String = "${original.ifBlank { "Untitled" }} (copy)"

    /**
     * "empty the note", "clear it", "/empty", "delete everything", … — natural-language and slash
     * forms of "wipe the note's body". Shared by the editor's AI dropdown and the AI terminal so
     * both surfaces perform the SAME instant, undoable local action instead of asking the AI.
     */
    private val emptyIntent = Regex(
        """^/?\s*(empty|clear|wipe|erase)(\s+(the\s+)?(note|body|it|text|everything|all|contents?))?\s*$|^delete\s+(everything|all( the)?( text| contents?)?)$""",
        RegexOption.IGNORE_CASE,
    )

    /** True when [input] asks for the note body to be emptied (see [emptyIntent]). */
    fun isEmptyIntent(input: String): Boolean = emptyIntent.matches(input.trim())

    /**
     * The human-readable lines printed by `/stats`. [masteredFraction] is 0..1 over the sampled
     * recently-reviewed cards; [reviewed] is that sample's size.
     */
    fun statsLines(total: Int, due: Int, reviewed: Int, masteredFraction: Float): List<String> {
        val pct = (masteredFraction.coerceIn(0f, 1f) * 100).toInt()
        return listOf(
            "cards: $total total · $due due",
            "reviewed (recent sample): $reviewed",
            "mastered: $pct% of sampled cards (stability ≥ 7d)",
        )
    }
}
