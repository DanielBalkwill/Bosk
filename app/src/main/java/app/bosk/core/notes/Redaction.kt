package app.bosk.core.notes

/**
 * Inline note redaction. A user can mark any selection as redacted; redacted spans are wrapped
 * in sentinel markers (⟦…⟧, chars unlikely to be typed) that are stored in the note body. The
 * editor renders the markers invisibly with a greyed background, and anything inside is removed
 * before the note is ever handed to the AI (read, summarise, upload, etc.) — so private bits
 * stay private.
 */
object Redaction {
    const val OPEN = '⟦'   // ⟦
    const val CLOSE = '⟧'  // ⟧
    private val REGION = Regex("⟦([^⟦⟧]*)⟧")

    fun hasMarkers(text: String): Boolean = text.indexOf(OPEN) >= 0

    /** What the AI is allowed to see: redacted spans replaced by a neutral placeholder. */
    fun forAi(text: String): String = REGION.replace(text) { "[redacted]" }

    /** Plain text without the markers (keeps the words) — for previews where styling isn't possible. */
    fun stripMarkers(text: String): String =
        text.replace(OPEN.toString(), "").replace(CLOSE.toString(), "")

    /** The text + new selection after wrapping/unwrapping the [start, end) range. */
    data class Result(val text: String, val selectionStart: Int, val selectionEnd: Int)

    /**
     * Redact the whole word at [offset] — or un-redact it if [offset] lands inside an existing
     * redacted region. Used by the editor's tap-to-redact mode. Returns the text unchanged (caret
     * only) when the tap isn't on a word.
     */
    fun redactWordAt(text: String, offset: Int): Result {
        if (text.isEmpty()) return Result(text, offset, offset)
        val pos = offset.coerceIn(0, text.length)
        enclosingRegion(text, pos)?.let { (s, e) ->
            val inner = text.substring(s + 1, e - 1) // drop the ⟦ ⟧ markers
            return Result(text.substring(0, s) + inner + text.substring(e), s, s + inner.length)
        }
        val bounds = wordBounds(text, pos) ?: return Result(text, pos, pos)
        val (ws, we) = bounds
        if (ws >= we) return Result(text, pos, pos)
        return toggle(text, ws, we)
    }

    private fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '\'' || c == '-' || c == '_'

    /** Inclusive-exclusive bounds of the word at [pos], or null if [pos] isn't on a word. */
    private fun wordBounds(text: String, pos: Int): Pair<Int, Int>? {
        var p = pos
        if (p >= text.length || !isWordChar(text[p])) {
            if (p > 0 && isWordChar(text[p - 1])) p-- else return null
        }
        var s = p
        while (s > 0 && isWordChar(text[s - 1])) s--
        var e = p + 1
        while (e < text.length && isWordChar(text[e])) e++
        return s to e
    }

    /** The [start, end) span (markers included) of the redacted region containing [pos], if any. */
    private fun enclosingRegion(text: String, pos: Int): Pair<Int, Int>? {
        REGION.findAll(text).forEach { m ->
            if (pos in m.range.first..m.range.last) return m.range.first to (m.range.last + 1)
        }
        return null
    }

    /** Redact the [start, end) selection — or un-redact it if it already contains markers. */
    fun toggle(text: String, start: Int, end: Int): Result {
        val s = start.coerceIn(0, text.length)
        val e = end.coerceIn(0, text.length)
        if (s >= e) return Result(text, s, e)
        val sel = text.substring(s, e)
        return if (sel.contains(OPEN) || sel.contains(CLOSE)) {
            val cleaned = stripMarkers(sel)
            Result(text.substring(0, s) + cleaned + text.substring(e), s, s + cleaned.length)
        } else {
            val wrapped = "$OPEN$sel$CLOSE"
            Result(text.substring(0, s) + wrapped + text.substring(e), s, s + wrapped.length)
        }
    }
}
