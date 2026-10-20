package com.generic.mnemos.ui.screens.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.generic.mnemos.core.notes.Markdown
import java.io.File

/**
 * Read-only Markdown rendering of a note body for the editor's preview mode. Renders the common
 * subset ([Markdown.blocks]) — headings, lists, quotes, fenced code, rules, `![[image]]` embeds,
 * and inline **bold** / *italic* / `code` / [[links]] — at the live [fontSp].
 */
@Composable
fun MarkdownView(
    body: String,
    fontSp: Float,
    imageFile: (String) -> File,
    isImageName: (String) -> Boolean,
    modifier: Modifier = Modifier,
    /** When set, tapping a task item's ☐/☑ glyph toggles it ([Int] = nth task in the body). */
    onToggleTask: ((Int) -> Unit)? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val blocks = Markdown.blocks(body)

    var taskCounter = 0 // document-order index of each task item, for onToggleTask
    Column(modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is Markdown.MdBlock.Heading -> {
                    val scale = when (block.level) { 1 -> 1.7f; 2 -> 1.4f; 3 -> 1.18f; else -> 1.08f }
                    Text(
                        inline(block.raw, accent, codeBg),
                        fontSize = (fontSp * scale).sp,
                        fontWeight = FontWeight.Bold,
                        color = headingColor(block.level),
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                is Markdown.MdBlock.Paragraph -> Text(
                    inline(block.raw, accent, codeBg),
                    fontSize = fontSp.sp,
                    color = onSurface,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                is Markdown.MdBlock.ListItem -> Row(Modifier.padding(vertical = 2.dp, horizontal = 4.dp)) {
                    val myTask = if (block.task) taskCounter++ else -1
                    Text(
                        when {
                            block.task && block.checked -> "☑  "
                            block.task -> "☐  "
                            block.ordered -> "${block.number}. "
                            else -> "•  "
                        },
                        fontSize = fontSp.sp,
                        color = accent,
                        modifier = if (block.task && onToggleTask != null) {
                            Modifier.clickable { onToggleTask(myTask) }.padding(end = 2.dp)
                        } else Modifier,
                    )
                    // A checked task reads as done: struck through and dimmed.
                    Text(
                        inline(block.raw, accent, codeBg),
                        fontSize = fontSp.sp,
                        color = if (block.task && block.checked) MaterialTheme.colorScheme.onSurfaceVariant else onSurface,
                        textDecoration = if (block.task && block.checked) TextDecoration.LineThrough else null,
                    )
                }
                is Markdown.MdBlock.Quote -> Row(
                    Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                ) {
                    Box(Modifier.width(3.dp).padding(vertical = 2.dp).background(accent))
                    Text(
                        inline(block.raw, accent, codeBg),
                        fontSize = fontSp.sp,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
                is Markdown.MdBlock.Code -> Surface(
                    color = codeBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Text(
                        block.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = (fontSp * 0.9f).sp,
                        color = onSurface,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                is Markdown.MdBlock.Image -> if (isImageName(block.name)) {
                    AsyncImage(
                        model = imageFile(block.name),
                        contentDescription = block.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(10.dp)),
                    )
                } else {
                    Text("![[${block.name}]]", fontSize = fontSp.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Markdown.MdBlock.Divider -> HorizontalDivider(Modifier.padding(vertical = 10.dp))
            }
        }
    }
}

/**
 * Fixed per-level heading tints (warm ramp, then a cool step for level 4 so it can't be confused
 * with 3). Shared by every note view — reading mode, the live editor, and raw source — so a
 * heading's level reads by colour, not just size. Fixed (not theme-derived): the brand palette
 * is the theme.
 */
internal fun headingColor(level: Int): Color = when (level) {
    1 -> Color(0xFFE05656) // red — the note's top structure
    2 -> Color(0xFFE08A4A) // ember orange
    3 -> Color(0xFFE0B34A) // amber
    else -> Color(0xFF6FB5A0) // muted teal — clearly "deeper than 3"
}

/** Build an AnnotatedString from inline markdown, styling bold/italic/strike/code/links. */
private fun inline(raw: String, linkColor: Color, codeBg: Color): AnnotatedString = buildAnnotatedString {
    Markdown.inlineSegments(raw).forEach { seg ->
        val style = when {
            seg.code -> SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)
            seg.link -> SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)
            else -> SpanStyle(
                fontWeight = if (seg.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (seg.italic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (seg.strike) TextDecoration.LineThrough else null,
            )
        }
        pushStyle(style)
        append(seg.text)
        pop()
    }
}
