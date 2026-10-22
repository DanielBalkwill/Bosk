package app.bosk.core.notes

/**
 * The text transforms behind the editor's formatting buttons (bold, italic, code, heading, bullet,
 * quote). Pure and selection-based so they're unit-testable; the Compose layer just feeds in the
 * current [androidx.compose.ui.text.input.TextFieldValue] text + selection and applies the result.
 */
object MarkdownEdit {

    /** Resulting text plus the new selection to restore after the edit. */
    data class Edit(val text: String, val selStart: Int, val selEnd: Int)

    /**
     * Wrap the [start, end) selection in [marker] (e.g. "**", "*", "`") — or unwrap it if it is
     * already wrapped. With an empty selection, insert an empty pair and place the caret inside.
     */
    fun toggleWrap(text: String, start: Int, end: Int, marker: String): Edit {
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(s, text.length)
        val m = marker.length
        // Empty selection → insert the pair, caret between the markers.
        if (s == e) {
            val newText = text.substring(0, s) + marker + marker + text.substring(s)
            return Edit(newText, s + m, s + m)
        }
        val sel = text.substring(s, e)
        // Already wrapped just inside the selection → unwrap.
        if (sel.length >= 2 * m && sel.startsWith(marker) && sel.endsWith(marker)) {
            val inner = sel.substring(m, sel.length - m)
            return Edit(text.substring(0, s) + inner + text.substring(e), s, s + inner.length)
        }
        // Wrapped just outside the selection → unwrap the surrounding markers.
        if (s >= m && e + m <= text.length &&
            text.substring(s - m, s) == marker && text.substring(e, e + m) == marker
        ) {
            val newText = text.substring(0, s - m) + sel + text.substring(e + m)
            return Edit(newText, s - m, e - m)
        }
        val newText = text.substring(0, s) + marker + sel + marker + text.substring(e)
        return Edit(newText, s + m, e + m)
    }

    /**
     * Toggle a line [prefix] (e.g. "# ", "- ", "> ") on the line containing [start]. Adding/removing
     * the prefix shifts the caret so it stays on the same character.
     */
    fun toggleLinePrefix(text: String, start: Int, prefix: String): Edit {
        val pos = start.coerceIn(0, text.length)
        val lineStart = lineStartOf(text, pos)
        val lineEndIdx = lineEndOf(text, lineStart)
        val line = text.substring(lineStart, lineEndIdx)
        return if (line.startsWith(prefix)) {
            val newText = text.substring(0, lineStart) + line.removePrefix(prefix) + text.substring(lineEndIdx)
            val caret = (pos - prefix.length).coerceAtLeast(lineStart)
            Edit(newText, caret, caret)
        } else {
            val newText = text.substring(0, lineStart) + prefix + line + text.substring(lineEndIdx)
            val caret = pos + prefix.length
            Edit(newText, caret, caret)
        }
    }

    /** True if the line containing [start] begins with [prefix] — the state its toggle button shows. */
    fun hasLinePrefix(text: String, start: Int, prefix: String): Boolean {
        val pos = start.coerceIn(0, text.length)
        return text.startsWith(prefix, lineStartOf(text, pos))
    }

    /**
     * Wrap the [start, end) selection in an asymmetric [open]/[close] pair (e.g. "[[" / "]]") — or
     * unwrap if it's already wrapped just inside or just outside the selection. An empty selection
     * inserts the pair with the caret between.
     */
    fun toggleWrapPair(text: String, start: Int, end: Int, open: String, close: String): Edit {
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(s, text.length)
        if (s == e) {
            val newText = text.substring(0, s) + open + close + text.substring(s)
            return Edit(newText, s + open.length, s + open.length)
        }
        val sel = text.substring(s, e)
        if (sel.length >= open.length + close.length && sel.startsWith(open) && sel.endsWith(close)) {
            val inner = sel.substring(open.length, sel.length - close.length)
            return Edit(text.substring(0, s) + inner + text.substring(e), s, s + inner.length)
        }
        if (s >= open.length && e + close.length <= text.length &&
            text.substring(s - open.length, s) == open && text.substring(e, e + close.length) == close
        ) {
            val newText = text.substring(0, s - open.length) + sel + text.substring(e + close.length)
            return Edit(newText, s - open.length, e - open.length)
        }
        return Edit(text.substring(0, s) + open + sel + close + text.substring(e), s + open.length, e + open.length)
    }

    private const val TASK_OPEN = "- [ ] "
    private const val TASK_DONE = "- [x] "
    private const val TASK_DONE_UPPER = "- [X] "

    /** True if the caret's line is a task item (checked or not) — the Checkbox button's ON state. */
    fun hasCheckbox(text: String, start: Int): Boolean {
        val ls = lineStartOf(text, start.coerceIn(0, text.length))
        return text.startsWith(TASK_OPEN, ls) || text.startsWith(TASK_DONE, ls) || text.startsWith(TASK_DONE_UPPER, ls)
    }

    /** Toggle a task checkbox prefix on the caret's line (a checked box counts as ON → removed). */
    fun toggleCheckbox(text: String, start: Int): Edit {
        val pos = start.coerceIn(0, text.length)
        val ls = lineStartOf(text, pos)
        val existing = listOf(TASK_OPEN, TASK_DONE, TASK_DONE_UPPER).firstOrNull { text.startsWith(it, ls) }
        return if (existing != null) {
            val newText = text.substring(0, ls) + text.substring(ls + existing.length)
            val caret = (pos - existing.length).coerceAtLeast(ls)
            Edit(newText, caret, caret)
        } else {
            val newText = text.substring(0, ls) + TASK_OPEN + text.substring(ls)
            Edit(newText, pos + TASK_OPEN.length, pos + TASK_OPEN.length)
        }
    }

    /** Insert a horizontal rule (`---`) on its own line at the caret; the caret lands after it. */
    fun insertDivider(text: String, start: Int): Edit {
        val pos = start.coerceIn(0, text.length)
        val before = if (pos == 0 || text[pos - 1] == '\n') "" else "\n"
        val after = if (pos < text.length && text[pos] == '\n') "" else "\n"
        val insert = before + "---" + after
        val newText = text.substring(0, pos) + insert + text.substring(pos)
        val caret = pos + insert.length
        return Edit(newText, caret, caret)
    }

    private val LINE_PREFIXES = Regex("""^(#{1,3} |> |- \[[ xX]] |- |\* |\+ |\d+\. )""")
    private val WIKI_LINK = Regex("""\[\[([^\[\]]+)]]""")

    /**
     * Strip markdown from the selection — inline markers (`**`, `*`, `~~`, backticks), `[[links]]`
     * (keeping the title), and any line prefixes (headings, lists, quotes, checkboxes) on the lines
     * the selection touches. A collapsed selection clears the caret's whole line. Selection expands
     * to full line bounds so a partial-line selection can't leave dangling markers behind.
     */
    fun clearFormatting(text: String, start: Int, end: Int): Edit {
        val s0 = start.coerceIn(0, text.length)
        val e0 = end.coerceIn(s0, text.length)
        val s = lineStartOf(text, s0)
        val e = lineEndOf(text, e0)
        val cleaned = text.substring(s, e)
            .split("\n")
            .joinToString("\n") { line ->
                var l = line
                // Peel stacked prefixes (e.g. "> - item") one layer at a time.
                while (true) {
                    val next = l.replaceFirst(LINE_PREFIXES, "")
                    if (next == l) break
                    l = next
                }
                l.replace(WIKI_LINK) { it.groupValues[1] }
                    .replace("**", "").replace("~~", "").replace("`", "").replace("*", "")
            }
        return Edit(text.substring(0, s) + cleaned + text.substring(e), s, s + cleaned.length)
    }

    private val ORDERED_PREFIX = Regex("""^(\d+)\. """)
    private val CONTINUABLE = listOf(TASK_OPEN, TASK_DONE, TASK_DONE_UPPER, "- ", "* ", "+ ", "> ")

    /**
     * Auto-continue lists on Enter. Call when a single newline was just typed, with [caret] = the
     * position right AFTER the '\n'. If the previous line was a list/task/quote/ordered item WITH
     * content, the next item's prefix is inserted at the caret (ordered numbers increment; a
     * checked task continues unchecked). If the previous line was an EMPTY item, its prefix is
     * removed instead — Enter-on-empty ends the list. Returns null when nothing applies.
     */
    fun continueList(text: String, caret: Int): Edit? {
        if (caret < 1 || caret > text.length || text[caret - 1] != '\n') return null
        val prevEnd = caret - 1
        val prevStart = lineStartOf(text, prevEnd)
        val prev = text.substring(prevStart, prevEnd)
        val ordered = ORDERED_PREFIX.find(prev)
        val prefix = if (ordered != null) null else CONTINUABLE.firstOrNull { prev.startsWith(it) }
        if (ordered == null && prefix == null) return null
        val markerLen = ordered?.value?.length ?: prefix!!.length
        val content = prev.substring(markerLen)
        return if (content.isBlank()) {
            // Empty item + Enter → end the list (strip the dangling prefix, keep the new line).
            val newText = text.substring(0, prevStart) + text.substring(prevStart + markerLen)
            Edit(newText, caret - markerLen, caret - markerLen)
        } else {
            val insert = when {
                ordered != null -> "${ordered.groupValues[1].toInt() + 1}. "
                prefix == TASK_DONE || prefix == TASK_DONE_UPPER -> TASK_OPEN
                else -> prefix!!
            }
            val newText = text.substring(0, caret) + insert + text.substring(caret)
            Edit(newText, caret + insert.length, caret + insert.length)
        }
    }

    private fun lineStartOf(text: String, pos: Int): Int =
        text.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }

    private fun lineEndOf(text: String, from: Int): Int =
        text.indexOf('\n', from).let { if (it < 0) text.length else it }
}
