package com.generic.mnemos

import com.generic.mnemos.core.notes.Markdown
import com.generic.mnemos.core.notes.Markdown.MdBlock
import com.generic.mnemos.core.notes.MarkdownEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    // ── blocks ──
    @Test
    fun headingsParsed() {
        assertEquals(MdBlock.Heading(1, "Title"), Markdown.blocks("# Title").single())
        assertEquals(MdBlock.Heading(3, "Sub"), Markdown.blocks("### Sub").single())
        // Level 4 is its own level (it used to fall through to level 3 with a stray '#').
        assertEquals(MdBlock.Heading(4, "Deep"), Markdown.blocks("#### Deep").single())
    }

    @Test
    fun consecutiveLinesMergeIntoOneParagraph() {
        val blocks = Markdown.blocks("hello\nworld\n\nnext")
        assertEquals(MdBlock.Paragraph("hello\nworld"), blocks[0])
        assertEquals(MdBlock.Paragraph("next"), blocks[1])
    }

    @Test
    fun bulletAndOrderedLists() {
        assertEquals(MdBlock.ListItem("a", ordered = false, number = 0), Markdown.blocks("- a").single())
        assertEquals(MdBlock.ListItem("b", ordered = true, number = 2), Markdown.blocks("2. b").single())
    }

    @Test
    fun fencedCodeBlockCollectsLines() {
        val blocks = Markdown.blocks("```\nval x = 1\nval y = 2\n```")
        assertEquals(MdBlock.Code("val x = 1\nval y = 2"), blocks.single())
    }

    @Test
    fun imageOnItsOwnLineIsImageBlock() {
        assertEquals(MdBlock.Image("pic.png"), Markdown.blocks("![[pic.png]]").single())
    }

    @Test
    fun dividerAndQuote() {
        assertEquals(MdBlock.Divider, Markdown.blocks("---").single())
        assertEquals(MdBlock.Quote("noted"), Markdown.blocks("> noted").single())
    }

    // ── inline segments ──
    @Test
    fun boldItalicCodeLinkSegments() {
        val segs = Markdown.inlineSegments("a **b** c *d* `e` [[f]]")
        assertTrue(segs.any { it.text == "b" && it.bold })
        assertTrue(segs.any { it.text == "d" && it.italic })
        assertTrue(segs.any { it.text == "e" && it.code })
        assertTrue(segs.any { it.text == "f" && it.link })
        assertTrue(segs.any { it.text == "a " && !it.bold && !it.italic })
    }

    @Test
    fun unclosedMarkerIsPlainText() {
        val segs = Markdown.inlineSegments("a `b")
        assertEquals("a `b", segs.joinToString("") { it.text })
    }

    // ── formatting edits ──
    @Test
    fun toggleWrapAddsAndRemovesBold() {
        val wrapped = MarkdownEdit.toggleWrap("hello world", 0, 5, "**")
        assertEquals("**hello** world", wrapped.text)
        // Selecting the inner text again and toggling removes it.
        val unwrapped = MarkdownEdit.toggleWrap(wrapped.text, wrapped.selStart, wrapped.selEnd, "**")
        assertEquals("hello world", unwrapped.text)
    }

    @Test
    fun toggleWrapEmptySelectionInsertsPairWithCaretInside() {
        val e = MarkdownEdit.toggleWrap("ab", 1, 1, "**")
        assertEquals("a****b", e.text)
        assertEquals(3, e.selStart)
        assertEquals(3, e.selEnd)
    }

    @Test
    fun toggleWrapUnwrapsMarkersJustOutsideSelection() {
        // caret selection covers "x" with the ** sitting outside it
        val e = MarkdownEdit.toggleWrap("**x**", 2, 3, "**")
        assertEquals("x", e.text)
    }

    @Test
    fun toggleLinePrefixAddsAndRemovesHeading() {
        val added = MarkdownEdit.toggleLinePrefix("title", 2, "# ")
        assertEquals("# title", added.text)
        assertEquals(4, added.selStart)
        val removed = MarkdownEdit.toggleLinePrefix(added.text, added.selStart, "# ")
        assertEquals("title", removed.text)
    }

    @Test
    fun toggleLinePrefixActsOnTheCaretsLine() {
        val e = MarkdownEdit.toggleLinePrefix("one\ntwo", 5, "- ")
        assertEquals("one\n- two", e.text)
    }

    // ── CP62: task items ──
    @Test
    fun taskItemsParsedWithCheckedState() {
        assertEquals(
            MdBlock.ListItem("todo", ordered = false, number = 0, task = true, checked = false),
            Markdown.blocks("- [ ] todo").single(),
        )
        assertEquals(
            MdBlock.ListItem("done", ordered = false, number = 0, task = true, checked = true),
            Markdown.blocks("- [x] done").single(),
        )
    }

    @Test
    fun toggleCheckboxAddsRemovesAndTreatsCheckedAsOn() {
        val added = MarkdownEdit.toggleCheckbox("task", 2)
        assertEquals("- [ ] task", added.text)
        val removed = MarkdownEdit.toggleCheckbox(added.text, added.selStart)
        assertEquals("task", removed.text)
        assertEquals("done", MarkdownEdit.toggleCheckbox("- [x] done", 8).text)
        assertTrue(MarkdownEdit.hasCheckbox("- [x] done", 3))
        assertTrue(!MarkdownEdit.hasCheckbox("- plain", 3))
    }

    // ── CP62: strikethrough ──
    @Test
    fun strikethroughSegments() {
        val segs = Markdown.inlineSegments("a ~~gone~~ b")
        assertTrue(segs.any { it.text == "gone" && it.strike })
        assertTrue(segs.any { it.text == "a " && !it.strike })
    }

    // ── CP62: caret-context states ──
    @Test
    fun inlineStateAtReflectsCaretContext() {
        val text = "plain **bold** and `code` here"
        assertTrue(Markdown.inlineStateAt(text, 9).bold)          // inside **bold**
        assertTrue(!Markdown.inlineStateAt(text, 2).bold)         // in plain text
        assertTrue(Markdown.inlineStateAt(text, 21).code)         // inside `code`
        assertTrue(Markdown.inlineStateAt("x ~~s~~", 5).strike)   // inside ~~s~~
        assertTrue(Markdown.inlineStateAt("*i* after", 2).italic) // inside *i*
    }

    @Test
    fun inlineStateAtScansOnlyTheCaretsLine() {
        // An unclosed ** on line 1 must not leak bold state into line 2.
        assertTrue(!Markdown.inlineStateAt("**open\nplain", 9).bold)
    }

    @Test
    fun hasLinePrefixMatchesTheCaretsLine() {
        assertTrue(MarkdownEdit.hasLinePrefix("# title\nbody", 3, "# "))
        assertTrue(!MarkdownEdit.hasLinePrefix("# title\nbody", 9, "# "))
    }

    // ── CP62: wrap pair / divider / clear ──
    @Test
    fun toggleWrapPairWrapsAndUnwrapsLinks() {
        val wrapped = MarkdownEdit.toggleWrapPair("see note here", 4, 8, "[[", "]]")
        assertEquals("see [[note]] here", wrapped.text)
        val unwrapped = MarkdownEdit.toggleWrapPair(wrapped.text, wrapped.selStart, wrapped.selEnd, "[[", "]]")
        assertEquals("see note here", unwrapped.text)
    }

    @Test
    fun insertDividerAddsOwnLineRule() {
        assertEquals("a\n---\nb", MarkdownEdit.insertDivider("a\nb", 2).text)
        assertEquals("---\n", MarkdownEdit.insertDivider("", 0).text)
    }

    // ── CP68: auto-continue lists on Enter ──
    @Test
    fun continueListInsertsNextBulletAndOrderedNumber() {
        val bullet = MarkdownEdit.continueList("- one\n", 6)
        assertEquals("- one\n- ", bullet!!.text)
        assertEquals(8, bullet.selStart)
        val ordered = MarkdownEdit.continueList("3. a\n", 5)
        assertEquals("3. a\n4. ", ordered!!.text)
    }

    @Test
    fun continueListChecksTaskAndEndsEmptyItem() {
        // A checked task continues as an UNCHECKED one.
        val task = MarkdownEdit.continueList("- [x] done\n", 11)
        assertEquals("- [x] done\n- [ ] ", task!!.text)
        // Enter on an empty item removes its prefix (ends the list).
        val ended = MarkdownEdit.continueList("- \n", 3)
        assertEquals("\n", ended!!.text)
        assertEquals(1, ended.selStart)
    }

    @Test
    fun continueListIgnoresPlainTextAndNonNewline() {
        assertEquals(null, MarkdownEdit.continueList("plain\n", 6))
        assertEquals(null, MarkdownEdit.continueList("- one", 3))
    }

    @Test
    fun clearFormattingStripsMarkersAndPrefixes() {
        val e = MarkdownEdit.clearFormatting("# **bold** and [[link]]", 0, 23)
        assertEquals("bold and link", e.text)
        // Collapsed selection clears the caret's whole line only.
        val e2 = MarkdownEdit.clearFormatting("- one\nplain", 2, 2)
        assertEquals("one\nplain", e2.text)
    }

    // ── outline ──
    @Test
    fun outlineFindsHeadingsWithLevelsAndOffsets() {
        val body = "# One\ntext\n## Two\n### Three\n#### Four"
        val o = Markdown.outline(body)
        assertEquals(4, o.size)
        assertEquals(Markdown.OutlineEntry(1, "One", 0), o[0])
        assertEquals(Markdown.OutlineEntry(2, "Two", 11), o[1])
        assertEquals(Markdown.OutlineEntry(3, "Three", 18), o[2])
        assertEquals(Markdown.OutlineEntry(4, "Four", 28), o[3])
    }

    @Test
    fun outlineSkipsFencedCodeAndNonHeadings() {
        val body = "```\n# not a heading\n```\n# Real\n#nospace\n#####too deep"
        val o = Markdown.outline(body)
        assertEquals(1, o.size)
        assertEquals("Real", o[0].text)
        assertEquals(24, o[0].offset)
    }

    @Test
    fun outlineEmptyOrPlainBodyIsEmpty() {
        assertEquals(0, Markdown.outline("").size)
        assertEquals(0, Markdown.outline("plain text only\nmore").size)
    }
}
