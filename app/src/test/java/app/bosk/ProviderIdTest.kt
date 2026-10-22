package app.bosk

import app.bosk.core.ai.llm.OpenAiCompatPreset
import app.bosk.core.ai.llm.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The "paste any key and it just works" promise rests entirely on prefix detection, so it's
 * worth locking down. Anthropic must be checked before OpenAI (its key also starts `sk-`).
 */
class ProviderIdTest {

    @Test
    fun detectsGeminiKey() {
        assertEquals(ProviderId.GEMINI, ProviderId.fromKey("AIzaSyA1b2C3d4E5f6"))
    }

    @Test
    fun detectsOpenAiKeys() {
        assertEquals(ProviderId.OPENAI, ProviderId.fromKey("sk-abcd1234"))
        assertEquals(ProviderId.OPENAI, ProviderId.fromKey("sk-proj-abcd1234"))
    }

    @Test
    fun detectsAnthropicKeyBeforeOpenAi() {
        // sk-ant- also matches the sk- prefix; order must resolve it to Anthropic.
        assertEquals(ProviderId.ANTHROPIC, ProviderId.fromKey("sk-ant-api03-abcd1234"))
    }

    @Test
    fun trimsWhitespaceBeforeDetecting() {
        assertEquals(ProviderId.OPENAI, ProviderId.fromKey("   sk-abcd  "))
    }

    @Test
    fun detectsOpenAiCompatibleKeys() {
        // The free, no-card backends all route to the universal OpenAI-compatible provider.
        assertEquals(ProviderId.OPENAI_COMPAT, ProviderId.fromKey("sk-or-v1-abcd1234")) // OpenRouter
        assertEquals(ProviderId.OPENAI_COMPAT, ProviderId.fromKey("gsk_abcd1234"))       // Groq
        assertEquals(ProviderId.OPENAI_COMPAT, ProviderId.fromKey("csk-abcd1234"))       // Cerebras
        assertEquals(ProviderId.OPENAI_COMPAT, ProviderId.fromKey("nvapi-abcd1234"))     // NVIDIA
    }

    @Test
    fun openRouterCheckedBeforePlainOpenAi() {
        // sk-or- also matches sk-; order must resolve it to the compat provider, not OpenAI.
        assertEquals(ProviderId.OPENAI_COMPAT, ProviderId.fromKey("sk-or-v1-xyz"))
    }

    @Test
    fun compatPresetMapsKnownPrefixes() {
        assertEquals(OpenAiCompatPreset.OPENROUTER, OpenAiCompatPreset.fromKey("sk-or-v1-xyz"))
        assertEquals(OpenAiCompatPreset.GROQ, OpenAiCompatPreset.fromKey("gsk_xyz"))
        assertEquals(OpenAiCompatPreset.CEREBRAS, OpenAiCompatPreset.fromKey("csk-xyz"))
        assertEquals(OpenAiCompatPreset.NVIDIA, OpenAiCompatPreset.fromKey("nvapi-xyz"))
        assertNull(OpenAiCompatPreset.fromKey("sk-plainopenai"))
    }

    @Test
    fun unrecognisedFormatReturnsNull() {
        assertNull(ProviderId.fromKey("not-a-key"))
        assertNull(ProviderId.fromKey(""))
    }

    @Test
    fun nanoIsKeyless_othersNeedKey() {
        assertEquals(false, ProviderId.NANO.needsKey)
        assertEquals(true, ProviderId.GEMINI.needsKey)
        assertEquals(true, ProviderId.OPENAI.needsKey)
        assertEquals(true, ProviderId.ANTHROPIC.needsKey)
    }
}
