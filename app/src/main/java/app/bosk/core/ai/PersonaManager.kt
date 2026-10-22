package app.bosk.core.ai

import app.bosk.core.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the current [Persona] into the system-prompt fragment merged into every AI call. Sits
 * between [SettingsRepository] (where the persona is persisted) and
 * [app.bosk.core.ai.llm.LlmRouter] (the single chokepoint that injects it), so persona
 * behaviour stays in one place and the router doesn't reach into settings directly.
 */
@Singleton
class PersonaManager @Inject constructor(
    private val settings: SettingsRepository,
) {
    /** The persona's system instruction, or "" if it resolves to nothing. Cheap (DataStore is cached). */
    suspend fun systemPrompt(): String =
        runCatching { settings.persona.first().systemPrompt() }.getOrDefault("")
}
