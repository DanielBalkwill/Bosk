package app.bosk.service.overlay

import app.bosk.core.ai.KnowledgeProfileManager
import app.bosk.core.ai.llm.LlmClient
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a free-text overlay command into an AI response. The flagship interaction:
 * the user types into the popup and Gemini answers, with the surfaced item as context.
 *
 * Structured commands (mark-as-known / remind-tomorrow / add-connection) are recognised
 * but their side effects are wired in CP5, once the overlay reliably carries the target's
 * review/note ids. For now those echo a confirmation; freeform + "explain more" fully work.
 */
@Singleton
class OverlayCommandHandler @Inject constructor(
    private val llm: LlmClient,
    private val profile: KnowledgeProfileManager,
) {
    fun isDismiss(command: String): Boolean =
        command.trim().lowercase() in setOf("skip", "dismiss", "close", "stop", "next")

    suspend fun systemContext(): String = profile.currentContextBlock()

    /** Stream the AI response for [command], grounded in the surfaced item. */
    fun stream(command: String, contextTitle: String, contextBody: String, systemContext: String): Flow<String> {
        val prompt = buildString {
            appendLine("The user is being shown this item in an ambient popup:")
            appendLine("Title: $contextTitle")
            appendLine("Content: $contextBody")
            appendLine()
            appendLine("User command: \"$command\"")
            append("Respond helpfully and concisely (popup-sized, no markdown).")
        }
        return llm.generateStream(prompt, systemContext)
    }
}
