package app.bosk.core.commands

import app.bosk.core.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The outcome of feeding a line of input through the command engine.
 *  • [Help]   — answer locally (no AI call), e.g. `/help`, `/help merge`.
 *  • [Clear]  — clear the surface's output/history (terminal `/clear`).
 *  • [Prompt] — text to actually send to the AI (a plain message, or an expanded slash command).
 */
sealed interface CommandResult {
    data class Help(val text: String) : CommandResult
    data object Clear : CommandResult
    data class Prompt(val text: String) : CommandResult
}

/**
 * Single source of truth for slash commands across every AI surface (terminal, tutor, note editor).
 * It reads the user-editable command note from [SettingsRepository], so every surface understands
 * exactly the commands documented there — add/rename one in Settings and it works everywhere.
 */
@Singleton
class CommandRegistry @Inject constructor(
    private val settings: SettingsRepository,
) {
    /** Parsed commands for palettes/dropdowns; recomputes when the note is edited. */
    val commands: Flow<List<CommandNote.CommandDoc>> =
        settings.commandNote.map { CommandNote.parse(it) }

    /** True if the line looks like a slash command worth routing through [process]. */
    fun isCommand(input: String): Boolean = input.trim().startsWith("/")

    /** Classify a raw line into help / clear / prompt, reading the live command note. */
    suspend fun process(input: String): CommandResult {
        val text = input.trim()
        if (!text.startsWith("/")) return CommandResult.Prompt(text)
        val note = settings.commandNote.first()
        return when {
            text == "/clear" -> CommandResult.Clear
            text == "/help" -> CommandResult.Help(CommandNote.quickReference(note))
            text == "/help:ai" -> CommandResult.Help(CommandNote.helpForSection(note, "AI"))
            text == "/help:privacy" -> CommandResult.Help(CommandNote.helpForSection(note, "PRIVACY"))
            text.startsWith("/help ") -> {
                val cmd = text.removePrefix("/help ").trim()
                CommandResult.Help(CommandNote.detail(note, cmd) ?: "No command \"$cmd\". Try /help for the full list.")
            }
            else -> CommandResult.Prompt(expand(text, note))
        }
    }

    /**
     * Turn a slash command into a concrete AI instruction using its documented summary as the
     * intent, plus whatever arguments the user typed. Unknown commands fall back to the literal
     * text (minus the slash) so nothing is silently dropped.
     */
    private fun expand(text: String, note: String): String {
        val name = text.substringBefore(' ').trim()
        val args = text.removePrefix(name).trim()
        val doc = CommandNote.parse(note).firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: return text.removePrefix("/")
        return buildString {
            // Phrase it as an ORDER, not documentation — a bare doc summary reads like a
            // description and models then DESCRIBE the command instead of performing it.
            append("Perform this now (do not describe or explain the task — actually do it): ")
            append(doc.summary.ifBlank { "run the $name command." })
            if (args.isNotBlank()) {
                append("\n\n")
                append(args)
            }
        }
    }
}
