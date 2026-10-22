package app.bosk.core.notes

/**
 * Minimal parser for Obsidian-style YAML frontmatter "properties" at the top of a note body:
 *
 * ```
 * ---
 * tags: [biology, cells]
 * ---
 * ```
 * or the block form
 * ```
 * ---
 * tags:
 *   - biology
 *   - cells
 * ---
 * ```
 *
 * We only need the `tags:` property. Supports the inline `[a, b]` / `a, b` forms and the block
 * `- item` form. Returned tags are trimmed and de-duped (order preserved), with surrounding
 * quotes and a leading `#` stripped. This is the graph's "colour tag" source, like Obsidian.
 */
object Frontmatter {

    /** The raw text between the leading `---` fence lines, or null if the note has no frontmatter. */
    private fun block(body: String): String? {
        val start = body.indexOfFirst { !it.isWhitespace() }
        if (start < 0 || !body.regionMatches(start, "---", 0, 3)) return null
        val firstNl = body.indexOf('\n', start)
        if (firstNl < 0) return null
        val close = body.indexOf("\n---", firstNl)
        if (close < 0) return null
        return body.substring(firstNl + 1, close)
    }

    private val TAGS_LINE = Regex("^\\s*tags\\s*:(.*)$", RegexOption.IGNORE_CASE)
    private val LIST_ITEM = Regex("^\\s*-\\s*(.+)$")

    /** All `tags:` values declared in the note's frontmatter (empty if none). */
    fun tags(body: String): List<String> {
        val b = block(body) ?: return emptyList()
        val lines = b.lines()
        val out = LinkedHashSet<String>()
        var i = 0
        while (i < lines.size) {
            val m = TAGS_LINE.find(lines[i])
            if (m != null) {
                val inline = m.groupValues[1].trim()
                if (inline.isNotEmpty()) {
                    inline.removePrefix("[").removeSuffix("]").split(",").forEach { add(out, it) }
                } else {
                    var j = i + 1
                    while (j < lines.size) {
                        val item = LIST_ITEM.find(lines[j]) ?: break
                        add(out, item.groupValues[1])
                        j++
                    }
                    i = j - 1
                }
            }
            i++
        }
        return out.toList()
    }

    private fun add(out: MutableSet<String>, raw: String) {
        val t = raw.trim().trim('"', '\'').trim().removePrefix("#").trim()
        if (t.isNotEmpty()) out.add(t)
    }
}
