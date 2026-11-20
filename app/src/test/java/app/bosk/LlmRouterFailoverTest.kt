package app.bosk

import app.bosk.core.ai.llm.LlmException
import app.bosk.core.ai.llm.LlmProvider
import app.bosk.core.ai.llm.LlmRouter
import app.bosk.core.ai.llm.ProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ordered multi-provider failover: the router must try keys/providers in priority order and
 * fall through to the next backup when one is down — the behaviour users rely on to never hit a
 * single provider's rate limit. Tested against [LlmRouter]'s pure helpers (no Android deps).
 */
class LlmRouterFailoverTest {

    private class FakeProvider(
        override val id: ProviderId,
        private val result: Result<String>,
        private val configured: Boolean = true,
    ) : LlmProvider {
        var calls = 0
        override fun isConfigured() = configured
        override suspend fun generate(prompt: String, systemContext: String?): Result<String> {
            calls++
            return result
        }
        override fun generateStream(prompt: String, systemContext: String?): Flow<String> = flow {
            result.fold(onSuccess = { emit(it) }, onFailure = { throw it })
        }
        override suspend fun validateKey(): Result<Unit> = result.map { }
    }

    private fun map(vararg providers: FakeProvider): Map<ProviderId, LlmProvider> =
        providers.associateBy { it.id }

    @Test
    fun usesPrimaryWhenItSucceeds() = runBlocking {
        val primary = FakeProvider(ProviderId.OPENAI, Result.success("primary"))
        val backup = FakeProvider(ProviderId.GEMINI, Result.success("backup"))
        val chain = LlmRouter.orderChain(listOf(ProviderId.OPENAI, ProviderId.GEMINI), map(primary, backup))
        assertEquals("primary", LlmRouter.generateOverChain(chain, "hi", null).getOrNull())
        assertEquals(0, backup.calls) // backup untouched when the primary works
    }

    @Test
    fun failsOverToBackupWhenPrimaryFails() = runBlocking {
        val primary = FakeProvider(ProviderId.OPENAI, Result.failure(LlmException("rate limited")))
        val backup = FakeProvider(ProviderId.GEMINI, Result.success("from backup"))
        val chain = LlmRouter.orderChain(listOf(ProviderId.OPENAI, ProviderId.GEMINI), map(primary, backup))
        assertEquals("from backup", LlmRouter.generateOverChain(chain, "hi", null).getOrNull())
        assertEquals(1, primary.calls)
        assertEquals(1, backup.calls)
    }

    @Test
    fun failsWhenEveryProviderInChainFails() = runBlocking {
        val a = FakeProvider(ProviderId.OPENAI, Result.failure(LlmException("a")))
        val b = FakeProvider(ProviderId.GEMINI, Result.failure(LlmException("b")))
        val chain = LlmRouter.orderChain(listOf(ProviderId.OPENAI, ProviderId.GEMINI), map(a, b))
        assertTrue(LlmRouter.generateOverChain(chain, "hi", null).isFailure)
    }

    @Test
    fun chainSkipsUnconfiguredAndKeepsPriorityOrder() {
        val primary = FakeProvider(ProviderId.OPENAI, Result.success("x"), configured = false)
        val backup = FakeProvider(ProviderId.GEMINI, Result.success("y"))
        val chain = LlmRouter.orderChain(listOf(ProviderId.OPENAI, ProviderId.GEMINI), map(primary, backup))
        assertEquals(listOf(ProviderId.GEMINI), chain.map { it.id })
    }

    @Test
    fun chainAppendsConfiguredProvidersNotInOrderAsBackups() {
        val a = FakeProvider(ProviderId.OPENAI, Result.success("x"))
        val b = FakeProvider(ProviderId.GEMINI, Result.success("y"))
        // Only OPENAI is explicitly ordered; GEMINI is configured but unlisted → appended as backup.
        val chain = LlmRouter.orderChain(listOf(ProviderId.OPENAI), map(a, b))
        assertEquals(listOf(ProviderId.OPENAI, ProviderId.GEMINI), chain.map { it.id })
    }
}
