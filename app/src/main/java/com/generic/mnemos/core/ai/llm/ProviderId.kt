package com.generic.mnemos.core.ai.llm

/**
 * The AI backends Mnemos can route through. BYOK stays the model — the user pastes *any*
 * provider key and we infer which one from its prefix ([fromKey]). NANO is the on-device
 * free tier (Gemini Nano via AICore): no key, runs on the phone, costs nothing.
 *
 * [needsKey] drives auto-selection: a keyless provider (Nano) is only used when the user
 * picks it explicitly — never auto-chosen — because its availability is device-dependent.
 */
enum class ProviderId(val displayName: String, val needsKey: Boolean) {
    NANO("On-device (Gemini Nano)", needsKey = false),
    GEMINI("Gemini", needsKey = true),
    OPENAI("OpenAI", needsKey = true),
    ANTHROPIC("Claude", needsKey = true),
    OPENAI_COMPAT("OpenAI-compatible", needsKey = true); // Groq / OpenRouter / DeepSeek / custom

    companion object {
        /**
         * Infer the provider from a pasted key's prefix:
         * - `sk-ant-…` → Anthropic (checked first; it also starts with `sk-`)
         * - `sk-or-…`  → OpenAI-compatible (OpenRouter)
         * - `gsk_…`    → OpenAI-compatible (Groq, free)
         * - `csk-…`    → OpenAI-compatible (Cerebras, free)
         * - `nvapi-…`  → OpenAI-compatible (NVIDIA NIM, free)
         * - `sk-…` / `sk-proj-…` → OpenAI (NB: DeepSeek keys also look like `sk-…`; reach
         *   DeepSeek via an explicit preset since the prefix is ambiguous)
         * - `AIza…` → Google / Gemini
         * Returns null for an unrecognised format so the UI can warn instead of guessing.
         */
        fun fromKey(key: String): ProviderId? {
            val k = key.trim()
            return when {
                k.startsWith("sk-ant-") -> ANTHROPIC
                k.startsWith("sk-or-") -> OPENAI_COMPAT
                k.startsWith("gsk_") -> OPENAI_COMPAT
                k.startsWith("csk-") -> OPENAI_COMPAT
                k.startsWith("nvapi-") -> OPENAI_COMPAT
                k.startsWith("sk-") -> OPENAI
                k.startsWith("AIza") -> GEMINI
                else -> null
            }
        }
    }
}
