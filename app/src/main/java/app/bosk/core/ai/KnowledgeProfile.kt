package app.bosk.core.ai

import kotlinx.serialization.Serializable

/**
 * The user's evolving knowledge state (the "Mulch pattern" payload). Kept compact on
 * purpose — it is prepended to every AI call as system context, so it must stay cheap.
 */
@Serializable
data class KnowledgeProfile(
    val strongTopics: List<String> = emptyList(),
    val weakTopics: List<String> = emptyList(),
    val activeTopics: List<String> = emptyList(),
    val totalCards: Int = 0,
    val retentionRate: Double = 0.0, // 0..1
    val updatedAtMs: Long = 0L,
) {
    /** Render as a short system-instruction block for Gemini. */
    fun toContextBlock(): String = buildString {
        appendLine("You are Bosk, the user's ambient learning companion.")
        appendLine("Use this profile of the user's current knowledge to tailor depth and connections:")
        if (activeTopics.isNotEmpty()) appendLine("- Actively learning: ${activeTopics.joinToString(", ")}")
        if (strongTopics.isNotEmpty()) appendLine("- Strong (can go advanced): ${strongTopics.joinToString(", ")}")
        if (weakTopics.isNotEmpty()) appendLine("- Weak (reinforce, simplify): ${weakTopics.joinToString(", ")}")
        if (totalCards > 0) {
            appendLine("- Review load: $totalCards cards, ~${(retentionRate * 100).toInt()}% retention.")
        }
        append("Be concise and connect new material to what they already know.")
    }
}
