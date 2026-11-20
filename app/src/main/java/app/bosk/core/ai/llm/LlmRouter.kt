package app.bosk.core.ai.llm

import app.bosk.core.ai.PersonaManager
import app.bosk.core.auth.SecureKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The [LlmClient] the app injects. Routes each call through an ordered **failover chain** of
 * configured providers ([SecureKeyStore.providerOrder]): it tries the primary first and falls
 * through to the next backup when a request fails (down / rate-limited / key rejected), so stacking
 * several providers' keys sidesteps any single provider's limit. A freshly-pasted key still routes
 * before it's been ordered; keyless Nano is only used when the user places it in the chain.
 */
@Singleton
class LlmRouter @Inject constructor(
    private val keyStore: SecureKeyStore,
    private val providers: Map<ProviderId, @JvmSuppressWildcards LlmProvider>,
    private val persona: PersonaManager,
) : LlmClient {

    /**
     * Providers to try, highest priority first: the user's explicit failover chain
     * ([SecureKeyStore.providerOrder]) filtered to configured providers, then any other configured
     * keyed provider they haven't ordered yet (so a freshly-pasted key still routes). Keyless Nano
     * only appears when the user placed it in the chain — its availability is device-dependent.
     */
    private fun orderedProviders(): List<LlmProvider> = orderChain(keyStore.providerOrder, providers)

    private fun active(): LlmProvider? = orderedProviders().firstOrNull()

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

    override suspend fun generate(prompt: String, systemContext: String?): Result<String> =
        generateOverChain(orderedProviders(), prompt, withPersona(systemContext))

    override fun generateStream(prompt: String, systemContext: String?): Flow<String> = flow {
        val chain = orderedProviders()
        if (chain.isEmpty()) throw noProviderError()
        val sys = withPersona(systemContext)
        var last: Throwable? = null
        for (p in chain) {
            var emitted = false
            try {
                p.generateStream(prompt, sys).collect { emitted = true; emit(it) }
                return@flow
            } catch (e: Throwable) {
                last = e
                if (emitted) throw e // already streaming this provider — can't fail over mid-reply
            }
        }
        throw last ?: noProviderError()
    }

    override suspend fun validateKey(): Result<Unit> {
        val p = active() ?: return Result.failure(noProviderError())
        return p.validateKey()
    }

    companion object {
        /**
         * Providers to try, highest priority first: [order] filtered to those present and configured
         * in [providers], then any other configured keyed provider not in [order]. Pure (unit-tested).
         */
        fun orderChain(
            order: List<ProviderId>,
            providers: Map<ProviderId, LlmProvider>,
        ): List<LlmProvider> {
            val primary = order.mapNotNull { id -> providers[id]?.takeIf { it.isConfigured() } }
            val rest = ProviderId.entries
                .filter { it.needsKey && it !in order }
                .mapNotNull { id -> providers[id]?.takeIf { it.isConfigured() } }
            return (primary + rest).distinctBy { it.id }
        }

        /** Try [chain] in order; first success wins, else the last failure. Pure (unit-tested). */
        suspend fun generateOverChain(
            chain: List<LlmProvider>,
            prompt: String,
            systemContext: String?,
        ): Result<String> {
            if (chain.isEmpty()) return Result.failure(noProviderError())
            var last: Throwable? = null
            for (p in chain) {
                val r = p.generate(prompt, systemContext)
                if (r.isSuccess) return r
                last = r.exceptionOrNull() // provider down / rejected the key → fall through to the next
            }
            return Result.failure(last ?: noProviderError())
        }

        private fun noProviderError() =
            LlmException("No AI provider set up yet. Add a Gemini, OpenAI or Claude key in Settings — or switch on the on-device helper.")
    }
}
