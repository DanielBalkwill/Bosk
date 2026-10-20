package com.generic.mnemos.core.review

import com.generic.mnemos.core.notes.Redaction

/**
 * Redaction → cloze cards. A redacted span (⟦…⟧) already marks "the part worth hiding" — which is
 * exactly what a cloze deletion is. Each span becomes a card: the FRONT is the surrounding context
 * with the span blanked to `[…]`, the BACK is the hidden text. Pure and unit-testable.
 */
object ClozeCards {
    data class Cloze(val front: String, val back: String)

    private val REGION = Regex("${Redaction.OPEN}([^${Redaction.OPEN}${Redaction.CLOSE}]*)${Redaction.CLOSE}")

    /** Extract one cloze per redacted span, with ~[contextChars] of context either side. */
    fun fromRedactions(body: String, contextChars: Int = 90): List<Cloze> {
        val out = ArrayList<Cloze>()
        REGION.findAll(body).forEach { m ->
            val hidden = m.groupValues[1].trim()
            if (hidden.isBlank()) return@forEach
            val start = m.range.first
            val endEx = m.range.last + 1
            val before = body.substring((start - contextChars).coerceAtLeast(0), start)
            val after = body.substring(endEx, (endEx + contextChars).coerceAtMost(body.length))
            // Other spans in the context stay hidden on the front (they're their own cards).
            val front = buildString {
                append(Redaction.forAi(before).trimStart())
                append(" […] ")
                append(Redaction.forAi(after).trimEnd())
            }.replace('\n', ' ').trim()
            out.add(Cloze(front = front, back = hidden))
        }
        return out
    }
}
