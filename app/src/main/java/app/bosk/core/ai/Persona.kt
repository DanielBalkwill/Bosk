package app.bosk.core.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The user's customisable "AI Partner" — how Bosk's AI talks to them. Edited on the AI Partner
 * screen and persisted in settings; merged into the system instruction of EVERY AI call by
 * [app.bosk.core.ai.llm.LlmRouter] (via [PersonaManager]), so one config governs the
 * tutor, the terminal, note AI, Home, and the ambient companion alike.
 *
 * Kept tiny and pure (no Android/Room) so [systemPrompt] composition is unit-testable.
 */
@Serializable
data class Persona(
    val name: String = "Bosk",
    val style: PersonaStyle = PersonaStyle.COMPANION,
    val verbosity: Verbosity = Verbosity.BALANCED,
    /** Free-text extra steering, always appended. The whole prompt for [PersonaStyle.CUSTOM]. */
    val customInstruction: String = "",
    val useEmoji: Boolean = false,
) {
    /** The persona fragment prepended (as system context) to AI calls. Empty only if truly blank. */
    fun systemPrompt(): String = buildString {
        val who = name.trim().ifBlank { "Bosk" }
        append("You are $who, the user's learning companion.")
        style.instruction.takeIf { it.isNotBlank() }?.let { append(' '); append(it) }
        append(' '); append(verbosity.instruction)
        if (!useEmoji) append(" Do not use emoji.")
        customInstruction.trim().takeIf { it.isNotEmpty() }?.let { append(' '); append(it) }
    }.trim()

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun encode(persona: Persona): String = json.encodeToString(serializer(), persona)

        /** Decode persisted JSON; blank or corrupt input falls back to the default persona. */
        fun decode(raw: String?): Persona =
            if (raw.isNullOrBlank()) Persona()
            else runCatching { json.decodeFromString(serializer(), raw) }.getOrDefault(Persona())
    }
}

/** A named teaching/conversation style. [instruction] is the directive injected into the prompt. */
enum class PersonaStyle(val display: String, val blurb: String, val instruction: String) {
    COMPANION(
        "Companion", "Warm, encouraging, connects ideas",
        "Speak warmly and encouragingly, connecting new material to what the user already knows.",
    ),
    SOCRATIC(
        "Socratic tutor", "Asks guiding questions",
        "Teach by asking guiding questions rather than handing over answers; lead the user to the insight themselves.",
    ),
    COACH(
        "Coach", "Direct, motivating, goal-focused",
        "Act as a direct, motivating coach: push the user, set concrete next steps, and hold them accountable.",
    ),
    EXPERT(
        "Terse expert", "Dense, technical, no fluff",
        "Answer like a terse domain expert: precise and technical, with no filler or pleasantries.",
    ),
    PEER(
        "Friendly peer", "Casual study buddy",
        "Talk like a relaxed, friendly study buddy at the user's level — conversational, not formal.",
    ),
    CUSTOM(
        "Custom", "Only your own instruction",
        "",
    ),
}

/** Reply-length preference, injected as a directive. */
enum class Verbosity(val display: String, val instruction: String) {
    BRIEF("Brief", "Keep replies short — a few sentences at most."),
    BALANCED("Balanced", "Keep replies a balanced length: thorough but not bloated."),
    DETAILED("Detailed", "Give detailed, comprehensive explanations with examples."),
}
