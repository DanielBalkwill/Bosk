package app.bosk.core.ai.llm

import app.bosk.core.ai.PersonaManager
import app.bosk.core.auth.SecureKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The [LlmClient] the app injects. Forwards each call to the *active* provider:
 *
 *  1. If the user explicitly chose a provider, honour it (including keyless Nano — its own
 *     [LlmProvider.generate] surfaces a clear error if the device can't run it).
 *  2. Otherwise auto-pick the first *keyed* provider that has a key, in [ProviderId] order.
 *
 * This is what makes "paste any key and it just works": saving a key sets the selection, and
 * routing follows. Nano is never auto-chosen (availability is device-dependent).
 */
@Singleton
class LlmRouter @Inject constructor(
    private val keyStore: SecureKeyStore,
    private val providers: Map<ProviderId, @JvmSuppressWildcards LlmProvider>,
    private val persona: PersonaManager,
) : LlmClient {

    private fun active(): LlmProvider? {
        keyStore.selectedProvider?.let { sel -> providers[sel]?.let { return it } }
        return ProviderId.entries.firstNotNullOfOrNull { id ->
            providers[id]?.takeIf { it.id.needsKey && it.isConfigured() }
        }
    }

    override val activeProvider: ProviderId? get() = active()?.id

    /**
     * Prepend the AI Partner persona to the caller's [systemContext] (Knowledge Profile block), so
     * every call across the app obeys the user's chosen persona. Persona first = dominant framing.
     */
    private suspend fun withPersona(systemContext: String?): String? =
        listOfNotNull(
            persona.systemPrompt().takeIf { it.isNotBlank() },
            systemContext?.takeIf { it.isNotBlank() },
        ).joinToString("\n\n").ifBlank { null }

    override suspend fun generate(prompt: String, systemContext: String?): Result<String> {
        val p = active() ?: return Result.failure(noProvider())
        return p.generate(prompt, withPersona(systemContext))
    }

    override fun generateStream(prompt: String, systemContext: String?): Flow<String> = flow {
        val p = active() ?: throw noProvider()
        emitAll(p.generateStream(prompt, withPersona(systemContext)))
    }

    override suspend fun validateKey(): Result<Unit> {
        val p = active() ?: return Result.failure(noProvider())
        return p.validateKey()
    }

    private fun noProvider() =
        LlmException("No AI provider set up yet. Add a Gemini, OpenAI or Claude key in Settings — or switch on the on-device helper.")
}
