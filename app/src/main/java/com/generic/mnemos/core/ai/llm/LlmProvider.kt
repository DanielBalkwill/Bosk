package com.generic.mnemos.core.ai.llm

import kotlinx.coroutines.flow.Flow

/**
 * A single AI backend. Each implementation owns one [ProviderId], reads its own key from
 * [com.generic.mnemos.core.auth.SecureKeyStore], and talks only to that vendor's endpoint
 * (or, for Nano, the on-device engine). The [LlmRouter] picks one at call time.
 */
interface LlmProvider {
    val id: ProviderId

    /** True when this backend can run now (key present, or device supports Nano). */
    fun isConfigured(): Boolean

    suspend fun generate(prompt: String, systemContext: String?): Result<String>

    fun generateStream(prompt: String, systemContext: String?): Flow<String>

    suspend fun validateKey(): Result<Unit>
}
