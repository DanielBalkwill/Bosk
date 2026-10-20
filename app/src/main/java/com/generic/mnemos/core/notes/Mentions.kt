package com.generic.mnemos.core.notes

/**
 * `#Note Name` mentions in the AI command box. A mention pulls the referenced note's contents
 * into the prompt. Matching is by known note titles (longest-first, case-insensitive) so that
 * multi-word titles work even though `#` has no closing delimiter — we accept the longest
 * known title that follows the `#`, ending at a non-alphanumeric boundary.
 */
object Mentions {

    /** A resolved mention span in the raw text: [start] is the `#`, [end] is exclusive. */
    data class Match(val title: String, val start: Int, val end: Int)

    /** The in-progress mention at the cursor (used to drive the suggestion dropdown). */
    data class Query(val start: Int, val query: String)

    fun find(text: String, titles: List<String>): List<Match> {
        val sorted = titles.filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }
        if (sorted.isEmpty()) return emptyList()
        val out = ArrayList<Match>()
        var i = 0
        while (i < text.length) {
            if (text[i] == '#') {
                val afterHash = i + 1
                val title = sorted.firstOrNull { t ->
                    afterHash + t.length <= text.length &&
                        text.regionMatches(afterHash, t, 0, t.length, ignoreCase = true) &&
                        endsAtBoundary(text, afterHash + t.length)
                }
                if (title != null) {
                    out.add(Match(title, i, afterHash + title.length))
                    i = afterHash + title.length
                    continue
                }
            }
            i++
        }
        return out
    }

    /**
     * The active `#…` being typed at [cursor], or null. Scans back to the nearest `#` on the
     * line; the query is everything between it and the cursor.
     */
    fun activeQuery(text: String, cursor: Int): Query? {
        if (cursor <= 0 || cursor > text.length) return null
        var i = cursor - 1
        while (i >= 0) {
            when (text[i]) {
                '\n' -> return null
                '#' -> return Query(start = i, query = text.substring(i + 1, cursor))
                else -> i--
            }
        }
        return null
    }

    /** Titles to suggest for [query]: prefix matches first, then contains. Capped to [limit]. */
    fun suggestions(query: String, titles: List<String>, limit: Int = 6): List<String> {
        val q = query.trim().lowercase()
        val pool = titles.filter { it.isNotBlank() }
        if (q.isEmpty()) return pool.take(limit)
        val prefix = pool.filter { it.lowercase().startsWith(q) }
        val contains = pool.filter { !it.lowercase().startsWith(q) && it.lowercase().contains(q) }
        return (prefix + contains).take(limit)
    }

    private fun endsAtBoundary(text: String, idx: Int): Boolean =
        idx >= text.length || !text[idx].isLetterOrDigit()
}
