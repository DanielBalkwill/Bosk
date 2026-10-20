package com.generic.mnemos.core.notes

/**
 * Obsidian-style note linking. A `[[Note Name]]` in a note body is a reference to another
 * note; `![[Note Name]]` is an embed (transclusion). Both create a connection on the graph.
 * Aliases (`[[Note|shown text]]`) and heading anchors (`[[Note#section]]`) are supported —
 * we link on the note name, ignoring the alias/anchor.
 *
 * There is also a lightweight bare form: `!Note Name` (no brackets) links to a known note by
 * title. Because `!` has no closing delimiter, bare links resolve against the list of existing
 * note titles, longest-title-first, ending at a non-alphanumeric boundary — the same trick the
 * `#mention` autocomplete uses. (A `![[…]]` embed is NOT a bare link; brackets take priority.)
 */
object WikiLinks {

    private val LINK = Regex("""(!?)\[\[([^\[\]]+?)]]""")

    /** A single parsed reference and where it sits in the raw text. */
    data class Ref(val name: String, val embed: Boolean, val start: Int, val end: Int)

    /** A bare `!Title` reference resolved against known titles. [start] is the `!`. */
    data class BareRef(val name: String, val start: Int, val end: Int)

    /** Every bracketed reference in [text], in order of appearance. */
    fun refs(text: String): List<Ref> =
        LINK.findAll(text).mapNotNull { m ->
            val name = cleanName(m.groupValues[2])
            if (name.isEmpty()) null
            else Ref(name = name, embed = m.groupValues[1] == "!", start = m.range.first, end = m.range.last + 1)
        }.toList()

    /**
     * Bare `!Title` references in [text], matched against [titles] (longest-first,
     * case-insensitive, ending at a non-alphanumeric boundary). `![[…]]` embeds are skipped.
     */
    fun bareRefs(text: String, titles: List<String>): List<BareRef> {
        val sorted = titles.filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }
        if (sorted.isEmpty()) return emptyList()
        val out = ArrayList<BareRef>()
        var i = 0
        while (i < text.length) {
            // A `!` that isn't the start of a `![[…]]` embed may begin a bare link.
            if (text[i] == '!' && (i + 1 >= text.length || text[i + 1] != '[')) {
                val after = i + 1
                val title = sorted.firstOrNull { t ->
                    after + t.length <= text.length &&
                        text.regionMatches(after, t, 0, t.length, ignoreCase = true) &&
                        endsAtBoundary(text, after + t.length)
                }
                if (title != null) {
                    out.add(BareRef(title, i, after + title.length))
                    i = after + title.length
                    continue
                }
            }
            i++
        }
        return out
    }

    /**
     * The in-progress bare `!…` link being typed at [cursor], as (indexOfBang, query), or null.
     * Scans back to the nearest `!` that starts a token (preceded by start/whitespace/punctuation),
     * staying on the current line. Drives the editor's note-suggestion dropdown.
     */
    fun bareActiveQuery(text: String, cursor: Int): Pair<Int, String>? {
        if (cursor <= 0 || cursor > text.length) return null
        var i = cursor - 1
        while (i >= 0 && cursor - i <= 48) {
            when (text[i]) {
                '\n' -> return null
                '!' -> {
                    val prevOk = i == 0 || !text[i - 1].isLetterOrDigit()
                    return if (prevOk) i to text.substring(i + 1, cursor) else null
                }
                else -> i--
            }
        }
        return null
    }

    /**
     * The in-progress `[[…` link being typed at [cursor], as (indexOfFirstBracket, query), or
     * null. Scans back on the current line for a `[[` with no `]` between it and the cursor —
     * a closed link is not "in progress". For an embed (`![[`) the returned index is still the
     * first `[`, so replacing from there keeps the `!`. Drives the editor's [[ autocomplete.
     */
    fun bracketActiveQuery(text: String, cursor: Int): Pair<Int, String>? {
        if (cursor <= 0 || cursor > text.length) return null
        var i = cursor - 1
        while (i >= 0 && cursor - i <= 64) {
            when (text[i]) {
                '\n', ']' -> return null
                '[' -> return if (i > 0 && text[i - 1] == '[') (i - 1) to text.substring(i + 1, cursor) else null
                else -> i--
            }
        }
        return null
    }

    /** The link name whose span contains [offset] (bracketed or bare `!Title`), or null. */
    fun linkAt(text: String, offset: Int, titles: List<String>): String? {
        refs(text).firstOrNull { offset in it.start..it.end }?.let { return it.name }
        bareRefs(text, titles).firstOrNull { offset in it.start..it.end }?.let { return it.name }
        return null
    }

    /** Distinct bracketed-link names (case-insensitive), preserving first-seen casing. */
    fun names(text: String): List<String> {
        val seen = LinkedHashMap<String, String>()
        refs(text).forEach { seen.putIfAbsent(it.name.lowercase(), it.name) }
        return seen.values.toList()
    }

    /** Distinct bare `!Title` names resolved against [titles] (case-insensitive). */
    fun bareNames(text: String, titles: List<String>): List<String> {
        val seen = LinkedHashMap<String, String>()
        bareRefs(text, titles).forEach { seen.putIfAbsent(it.name.lowercase(), it.name) }
        return seen.values.toList()
    }

    private fun endsAtBoundary(text: String, idx: Int): Boolean =
        idx >= text.length || !text[idx].isLetterOrDigit()

    private fun cleanName(raw: String): String =
        raw.substringBefore('|').substringBefore('#').trim()
}
