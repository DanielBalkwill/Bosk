package com.generic.mnemos.core.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One time-blocked step in the [DailyRoadmap]. The AI returns these as a JSON array; the Home
 * card renders each with its own section colour (see [RoadmapPalette]), a bold coloured [time],
 * and chips for any [notes] it references.
 */
@Serializable
data class RoadmapStep(
    /** A short time block, e.g. "09:00–09:30" or "Morning". Rendered bold in the section colour. */
    val time: String = "",
    /** What to do in this block. */
    val title: String = "",
    /** Optional one-line elaboration. */
    val detail: String = "",
    /** Titles of notes/topics this step touches (shown as colour-tinted chips). */
    val notes: List<String> = emptyList(),
)

/**
 * A learning plan for a single day. [epochDay] is `LocalDate.toEpochDay()` at generation time so
 * Home can show today's saved plan without regenerating (and replace a stale one).
 */
@Serializable
data class DailyRoadmap(
    val steps: List<RoadmapStep> = emptyList(),
    val epochDay: Long = 0L,
) {
    fun isForDay(day: Long): Boolean = epochDay == day && steps.isNotEmpty()

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun encode(roadmap: DailyRoadmap): String = json.encodeToString(roadmap)

        /** Decode a saved roadmap; returns null on blank/garbage so callers fall back to "none". */
        fun decode(raw: String?): DailyRoadmap? {
            if (raw.isNullOrBlank()) return null
            return runCatching { json.decodeFromString<DailyRoadmap>(raw) }.getOrNull()
        }
    }
}

/**
 * A small fixed palette of warm-compatible section colours (argb ints) so each roadmap step gets
 * a stable, distinct colour. Kept as ints (not Compose Color) so it lives in core with no UI deps.
 */
object RoadmapPalette {
    val colors: List<Int> = listOf(
        0xFFC94040.toInt(), // brand red
        0xFFE0954C.toInt(), // amber
        0xFF5CBF8A.toInt(), // green
        0xFF4C9BE0.toInt(), // blue
        0xFFB073E0.toInt(), // violet
        0xFF35C2B0.toInt(), // teal
    )

    /** Stable colour for a step at [index] (cycles through [colors]). */
    fun colorForIndex(index: Int): Int = colors[((index % colors.size) + colors.size) % colors.size]
}

/**
 * Time-of-day maths for roadmap steps (pure, unit-testable). Steps carry free-text AI-generated
 * times ("09:00", "9:30 AM", "14.00–15.00"), so parsing is tolerant: the FIRST recognisable
 * clock time in the string is the step's start.
 */
object RoadmapTime {
    private val TIME = Regex("""(\d{1,2})(?:[:.](\d{2}))?\s*([ap])\.?m?\.?""", RegexOption.IGNORE_CASE)
    private val TIME_24 = Regex("""(\d{1,2})[:.](\d{2})""")

    /** Parse a step's start time to minutes-of-day (0..1439), or null when unparseable. */
    fun startMinutes(time: String): Int? {
        val t = time.trim()
        if (t.isEmpty()) return null
        // Prefer an am/pm form; otherwise a 24h "HH:mm" form.
        TIME.find(t)?.let { m ->
            var h = m.groupValues[1].toIntOrNull() ?: return@let
            val min = m.groupValues[2].toIntOrNull() ?: 0
            val ap = m.groupValues[3].lowercase()
            if (ap == "p" && h in 1..11) h += 12
            if (ap == "a" && h == 12) h = 0
            if (h <= 23 && min <= 59) return h * 60 + min
        }
        TIME_24.find(t)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: return@let
            val min = m.groupValues[2].toIntOrNull() ?: 0
            if (h <= 23 && min <= 59) return h * 60 + min
        }
        return null
    }

    /**
     * Indices of steps whose block is already OVER: a step is past once any LATER step's start
     * time has been reached (its block ended when the next one began). The current block and
     * future blocks stay unstruck; steps with unparseable times are never struck.
     */
    fun pastStepIndices(steps: List<RoadmapStep>, nowMinutes: Int): Set<Int> {
        val starts = steps.map { startMinutes(it.time) }
        val out = HashSet<Int>()
        for (i in steps.indices) {
            if (starts[i] == null) continue
            val laterStarted = (i + 1 until steps.size).any { j ->
                starts[j]?.let { it <= nowMinutes } == true
            }
            if (laterStarted) out.add(i)
        }
        return out
    }
}
