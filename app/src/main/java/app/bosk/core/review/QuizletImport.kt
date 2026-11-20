package app.bosk.core.review

/**
 * Imports a Quizlet study set from the text produced by Quizlet's **Export** dialog (or a plain
 * copy-paste of a set). Quizlet has no public API and prohibits scraping, so the reliable, ToS-clean
 * path is: in Quizlet, open a set → ••• → Export → copy, then paste the text into Bosk.
 *
 * Quizlet lets you choose the delimiter **between term and definition** (default TAB) and the one
 * **between cards** (default new line). This parser handles the default plus the common custom
 * choices: ` - `, ` — `, ` : ` between term and definition, and `;` between cards when new lines
 * aren't used. Everything here is pure (no Android), so it's fully unit-tested.
 */
object QuizletImport {

    data class Card(val term: String, val definition: String)

    /** Term↔definition separators tried in order (tab first — Quizlet's default). */
    private val TERM_DEF_SEPARATORS = listOf("\t", " - ", " — ", " : ", "  ")

    /** Parse pasted Quizlet text into term/definition cards. Malformed/blank rows are skipped. */
    fun parse(text: String): List<Card> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        // Rows are separated by new lines when present, otherwise by ';'.
        val rows = if ('\n' in trimmed) trimmed.split('\n') else trimmed.split(';')
        return rows.mapNotNull { row ->
            val line = row.trim().trim(';').trim()
            if (line.isEmpty()) return@mapNotNull null
            val split = splitTermDef(line) ?: return@mapNotNull null
            val term = split.first.trim()
            val def = split.second.trim()
            if (term.isEmpty() || def.isEmpty()) null else Card(term, def)
        }
    }

    private fun splitTermDef(line: String): Pair<String, String>? {
        for (sep in TERM_DEF_SEPARATORS) {
            val idx = line.indexOf(sep)
            if (idx > 0) return line.substring(0, idx) to line.substring(idx + sep.length)
        }
        return null
    }
}
