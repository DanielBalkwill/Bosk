package com.generic.mnemos.core.notes

/**
 * A small, dependency-free Markdown model for the editor's live preview and its formatting toolbar.
 * Pure (no Compose/Android) so the block split, inline-span parse, and the toolbar edit operations
 * are all unit-testable; the Compose layer ([com.generic.mnemos.ui.screens.notes] MarkdownView)
 * just renders the [MdBlock]s and [MdSegment]s this produces.
 *
 * Deliberately covers the common subset: ATX headings, bullet/ordered lists, blockquotes, fenced
 * code, horizontal rules, `![[image]]` embeds, and inline **bold** / *italic* / `code` / [[links]].
 */
object Markdown {

    // ── Block model ────────────────────────────────────────────────────────────────────────────
    sealed interface MdBlock {
        data class Heading(val level: Int, val raw: String) : MdBlock
        data class ListItem(
            val raw: String,
            val ordered: Boolean,
            val number: Int,
            /** True for a `- [ ]` / `- [x]` task item; [checked] then reflects the box. */
            val task: Boolean = false,
            val checked: Boolean = false,
        ) : MdBlock
        data class Quote(val raw: String) : MdBlock
        data class Code(val text: String) : MdBlock
        data class Image(val name: String) : MdBlock
        data class Paragraph(val raw: String) : MdBlock
        data object Divider : MdBlock
    }

    private val ORDERED = Regex("""^(\d+)\.\s+(.*)""")
    private val IMAGE_ONLY = Regex("""^!\[\[([^\[\]]+)]]$""")

    /** Split [body] into renderable blocks. Consecutive plain lines merge into one paragraph. */
    fun blocks(body: String): List<MdBlock> {
        val out = ArrayList<MdBlock>()
        val lines = body.split("\n")
        val para = StringBuilder()
        fun flushPara() {
            if (para.isNotEmpty()) { out.add(MdBlock.Paragraph(para.toString().trimEnd('\n'))); para.clear() }
        }
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.startsWith("```") -> {
                    flushPara()
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) { code.appendLine(lines[i]); i++ }
                    out.add(MdBlock.Code(code.toString().trimEnd('\n')))
                }
                trimmed.isEmpty() -> flushPara()
                trimmed == "---" || trimmed == "***" || trimmed == "___" -> { flushPara(); out.add(MdBlock.Divider) }
                IMAGE_ONLY.matches(trimmed) -> {
                    flushPara()
                    out.add(MdBlock.Image(IMAGE_ONLY.find(trimmed)!!.groupValues[1]))
                }
                trimmed.startsWith("#### ") -> { flushPara(); out.add(MdBlock.Heading(4, trimmed.removePrefix("#### "))) }
                trimmed.startsWith("### ") -> { flushPara(); out.add(MdBlock.Heading(3, trimmed.removePrefix("### "))) }
                trimmed.startsWith("## ") -> { flushPara(); out.add(MdBlock.Heading(2, trimmed.removePrefix("## "))) }
                trimmed.startsWith("# ") -> { flushPara(); out.add(MdBlock.Heading(1, trimmed.removePrefix("# "))) }
                trimmed.startsWith("> ") -> { flushPara(); out.add(MdBlock.Quote(trimmed.removePrefix("> "))) }
                // Task items must match BEFORE the plain "- " branch.
                trimmed.startsWith("- [ ] ") -> {
                    flushPara()
                    out.add(MdBlock.ListItem(trimmed.substring(6), ordered = false, number = 0, task = true, checked = false))
                }
                trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> {
                    flushPara()
                    out.add(MdBlock.ListItem(trimmed.substring(6), ordered = false, number = 0, task = true, checked = true))
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                    flushPara(); out.add(MdBlock.ListItem(trimmed.substring(2), ordered = false, number = 0))
                }
                ORDERED.matches(trimmed) -> {
                    flushPara()
                    val m = ORDERED.find(trimmed)!!
                    out.add(MdBlock.ListItem(m.groupValues[2], ordered = true, number = m.groupValues[1].toInt()))
                }
                else -> { if (para.isNotEmpty()) para.append('\n'); para.append(line) }
            }
            i++
        }
        flushPara()
        return out
    }

    // ── Outline ────────────────────────────────────────────────────────────────────────────────
    /** One heading in a note, with the char offset of its line start (for jump-to-heading). */
    data class OutlineEntry(val level: Int, val text: String, val offset: Int)

    /** Every ATX heading in [body] in document order; headings inside fenced code are skipped. */
    fun outline(body: String): List<OutlineEntry> {
        if (body.isEmpty()) return emptyList()
        val out = ArrayList<OutlineEntry>()
        var offset = 0
        var inFence = false
        body.split("\n").forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("```")) {
                inFence = !inFence
            } else if (!inFence) {
                val level = when {
                    trimmed.startsWith("# ") -> 1
                    trimmed.startsWith("## ") -> 2
                    trimmed.startsWith("### ") -> 3
                    trimmed.startsWith("#### ") -> 4
                    else -> 0
                }
                if (level > 0) out.add(OutlineEntry(level, trimmed.substring(level + 1).trim(), offset))
            }
            offset += line.length + 1
        }
        return out
    }

    // ── Inline spans ───────────────────────────────────────────────────────────────────────────
    /** A styled run of text for the renderer to turn into an AnnotatedString span. */
    data class MdSegment(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val link: Boolean = false,
        val strike: Boolean = false,
    )

    /**
     * Parse inline markers in [text] into styled segments. Handles `code` (highest precedence, no
     * nesting), **bold**, *italic*, ~~strikethrough~~, and [[links]]. Unclosed markers are emitted
     * as plain text.
     */
    fun inlineSegments(text: String): List<MdSegment> {
        val out = ArrayList<MdSegment>()
        val sb = StringBuilder()
        var bold = false
        var italic = false
        var strike = false
        fun flush() { if (sb.isNotEmpty()) { out.add(MdSegment(sb.toString(), bold, italic, strike = strike)); sb.clear() } }
        var i = 0
        while (i < text.length) {
            val rest = text.length - i
            when {
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) { flush(); out.add(MdSegment(text.substring(i + 1, end), code = true)); i = end + 1 }
                    else { sb.append(text[i]); i++ }
                }
                rest >= 4 && text.startsWith("[[", i) -> {
                    val end = text.indexOf("]]", i + 2)
                    if (end > i) { flush(); out.add(MdSegment(text.substring(i + 2, end), link = true)); i = end + 2 }
                    else { sb.append(text[i]); i++ }
                }
                rest >= 2 && text.startsWith("**", i) -> { flush(); bold = !bold; i += 2 }
                rest >= 2 && text.startsWith("~~", i) -> { flush(); strike = !strike; i += 2 }
                text[i] == '*' -> { flush(); italic = !italic; i++ }
                else -> { sb.append(text[i]); i++ }
            }
        }
        flush()
        return out.filter { it.text.isNotEmpty() }
    }

    /** The inline formatting active at a caret position — drives the toolbar's live toggle states. */
    data class InlineState(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        val strike: Boolean = false,
    )

    /**
     * What inline formatting is active at [offset]? Scans only the offset's line (inline markers
     * don't span lines), mirroring [inlineSegments]'s marker rules, so the toolbar toggles light
     * up when the caret sits inside **bold** / *italic* / ~~strike~~ / `code`.
     */
    fun inlineStateAt(text: String, offset: Int): InlineState {
        val pos = offset.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (pos - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, lineEnd)
        val rel = pos - lineStart
        var bold = false
        var italic = false
        var strike = false
        var i = 0
        while (i < line.length && i < rel) {
            val rest = line.length - i
            when {
                line[i] == '`' -> {
                    val end = line.indexOf('`', i + 1)
                    if (end > i) {
                        if (rel in (i + 1)..end) return InlineState(bold, italic, code = true, strike = strike)
                        i = end + 1
                    } else i++
                }
                rest >= 2 && line.startsWith("**", i) -> { bold = !bold; i += 2 }
                rest >= 2 && line.startsWith("~~", i) -> { strike = !strike; i += 2 }
                line[i] == '*' -> { italic = !italic; i++ }
                else -> i++
            }
        }
        return InlineState(bold, italic, code = false, strike = strike)
    }
}
