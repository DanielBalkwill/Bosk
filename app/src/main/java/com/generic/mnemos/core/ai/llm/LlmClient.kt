package com.generic.mnemos.core.ai.llm

import kotlinx.coroutines.flow.Flow

/**
 * Provider-agnostic AI entry point used by the whole app. Implemented by [LlmRouter], which
 * forwards to whichever [LlmProvider] is active (Gemini / OpenAI / Claude / on-device Nano).
 *
 * All calls are suspend/Flow (global rule #4 — never block the UI) and return [Result] so
 * callers can show friendly errors (e.g. "no key set"). [systemContext] is the Knowledge
 * Profile block (Mulch pattern), passed to each backend as its system instruction.
 */
interface LlmClient {
    /** One-shot generation. Never throws — failures come back as [Result.failure]. */
    suspend fun generate(prompt: String, systemContext: String? = null): Result<String>

    /** Streamed generation — emits text chunks as they arrive, for live TTS hand-off. */
    fun generateStream(prompt: String, systemContext: String? = null): Flow<String>

    /** Lightweight validity probe for the active provider (onboarding / settings). */
    suspend fun validateKey(): Result<Unit>

    /** Which provider calls currently route to, for UI display; null if none configured. */
    val activeProvider: ProviderId?
}

/** Uniform error across providers. [retryable] marks transient server faults worth a backoff. */
class LlmException(message: String, val retryable: Boolean = false) : Exception(message)
