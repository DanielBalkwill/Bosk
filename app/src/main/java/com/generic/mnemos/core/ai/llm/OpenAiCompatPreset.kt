package com.generic.mnemos.core.ai.llm

/**
 * Ready-made endpoints for the universal OpenAI-compatible provider. They all speak the same
 * `/chat/completions` wire format, so one provider + a base-URL/model pair reaches any of them.
 *
 * [free] flags the ones that hand out a usable key with **no payment** — the answer for users
 * who can't add billing. CUSTOM lets advanced users point at anything else (Together, Fireworks,
 * a self-hosted vLLM, etc.).
 */
enum class OpenAiCompatPreset(
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
    val keyHint: String,
    val signupUrl: String,
    val free: Boolean,
) {
    OPENROUTER(
        label = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        defaultModel = "deepseek/deepseek-chat-v3-0324:free",
        keyHint = "sk-or-…",
        signupUrl = "https://openrouter.ai/keys",
        free = true,
    ),
    GROQ(
        label = "Groq",
        baseUrl = "https://api.groq.com/openai/v1",
        defaultModel = "llama-3.3-70b-versatile",
        keyHint = "gsk_…",
        signupUrl = "https://console.groq.com/keys",
        free = true,
    ),
    CEREBRAS(
        label = "Cerebras",
        baseUrl = "https://api.cerebras.ai/v1",
        defaultModel = "llama-3.3-70b",
        keyHint = "csk-…",
        signupUrl = "https://cloud.cerebras.ai",
        free = true,
    ),
    NVIDIA(
        label = "NVIDIA NIM",
        baseUrl = "https://integrate.api.nvidia.com/v1",
        defaultModel = "meta/llama-3.3-70b-instruct",
        keyHint = "nvapi-…",
        signupUrl = "https://build.nvidia.com",
        free = true,
    ),
    DEEPSEEK(
        label = "DeepSeek",
        baseUrl = "https://api.deepseek.com/v1",
        defaultModel = "deepseek-chat",
        keyHint = "sk-…",
        signupUrl = "https://platform.deepseek.com/api_keys",
        free = false,
    ),
    CUSTOM(
        label = "Custom",
        baseUrl = "",
        defaultModel = "",
        keyHint = "any",
        signupUrl = "",
        free = false,
    );

    companion object {
        /** Unambiguous prefixes we can auto-configure on paste (the free no-card providers). */
        fun fromKey(key: String): OpenAiCompatPreset? {
            val k = key.trim()
            return when {
                k.startsWith("sk-or-") -> OPENROUTER
                k.startsWith("gsk_") -> GROQ
                k.startsWith("csk-") -> CEREBRAS
                k.startsWith("nvapi-") -> NVIDIA
                else -> null
            }
        }
    }
}
