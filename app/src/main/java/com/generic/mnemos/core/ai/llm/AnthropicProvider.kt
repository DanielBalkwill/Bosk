package com.generic.mnemos.core.ai.llm

import com.generic.mnemos.core.ai.RpmRateLimiter
import com.generic.mnemos.core.ai.model.AnthropicMessage
import com.generic.mnemos.core.ai.model.AnthropicRequest
import com.generic.mnemos.core.ai.model.AnthropicResponse
import com.generic.mnemos.core.ai.model.AnthropicStreamEvent
import com.generic.mnemos.core.ai.model.text
import com.generic.mnemos.core.auth.SecureKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BYOK Anthropic (Claude) provider — Messages API. Key from [SecureKeyStore], sent only to
 * Anthropic via the `x-api-key` header. System prompt is a top-level field, not a message.
 * Defaults to Claude 3.5 Haiku (cheap/fast); swap [MODEL] to use a larger model.
 */
@Singleton
class AnthropicProvider @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
    private val keyStore: SecureKeyStore,
) : LlmProvider {

    override val id = ProviderId.ANTHROPIC
    override fun isConfigured() = keyStore.hasKey(ProviderId.ANTHROPIC)

    private val rateLimiter = RpmRateLimiter(maxPerMinute = 20)

    override suspend fun generate(prompt: String, systemContext: String?): Result<String> =
        withContext(Dispatchers.IO) {
            val key = key() ?: return@withContext Result.failure(LlmException("No Claude API key set."))
            val body = json.encodeToString(AnthropicRequest.serializer(), buildRequest(prompt, systemContext, stream = false))
                .toRequestBody(JSON_MEDIA)
            runCatching {
                rateLimiter.acquire()
                http.newCall(post(key, body)).execute().use { resp ->
                    val payload = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw httpError(resp.code, payload)
                    json.decodeFromString(AnthropicResponse.serializer(), payload).text()
                }
            }
        }

    override fun generateStream(prompt: String, systemContext: String?): Flow<String> = flow {
        val key = key() ?: throw LlmException("No Claude API key set.")
        rateLimiter.acquire()
        val body = json.encodeToString(AnthropicRequest.serializer(), buildRequest(prompt, systemContext, stream = true))
            .toRequestBody(JSON_MEDIA)
        http.newCall(post(key, body)).execute().use { resp ->
            val source = resp.body?.source() ?: throw LlmException("Empty stream body.")
            if (!resp.isSuccessful) throw httpError(resp.code, resp.body?.string().orEmpty())
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                // Anthropic SSE interleaves "event:" and "data:" lines; we only parse data.
                if (!line.startsWith("data:")) continue
                val chunk = line.removePrefix("data:").trim()
                if (chunk.isEmpty()) continue
                val piece = runCatching {
                    val ev = json.decodeFromString(AnthropicStreamEvent.serializer(), chunk)
                    if (ev.type == "content_block_delta") ev.delta?.text else null
                }.getOrNull()
                if (!piece.isNullOrEmpty()) emit(piece)
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun validateKey(): Result<Unit> = withContext(Dispatchers.IO) {
        val key = key() ?: return@withContext Result.failure(LlmException("No Claude API key set."))
        runCatching {
            val request = Request.Builder().url("$BASE_URL/models")
                .header("x-api-key", key)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .get().build()
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw httpError(resp.code, resp.body?.string().orEmpty())
            }
        }
    }

    private fun key() = keyStore.key(ProviderId.ANTHROPIC)

    private fun post(key: String, body: okhttp3.RequestBody) = Request.Builder()
        .url("$BASE_URL/messages")
        .header("x-api-key", key)
        .header("anthropic-version", ANTHROPIC_VERSION)
        .post(body).build()

    private fun buildRequest(prompt: String, systemContext: String?, stream: Boolean) = AnthropicRequest(
        model = MODEL,
        maxTokens = 1024,
        system = systemContext?.takeIf { it.isNotBlank() },
        messages = listOf(AnthropicMessage("user", prompt)),
        stream = stream,
    )

    private fun httpError(code: Int, payload: String): LlmException {
        val retryable = code == 503 || code == 500 || code == 529
        val msg = when (code) {
            401 -> "Claude key rejected (401) — check the key."
            429 -> "Claude rate/quota limit hit (429)."
            else -> "Claude HTTP $code"
        }
        return LlmException("$msg ${payload.take(300)}".trim(), retryable = retryable)
    }

    companion object {
        const val BASE_URL = "https://api.anthropic.com/v1"
        const val MODEL = "claude-3-5-haiku-latest"
        const val ANTHROPIC_VERSION = "2023-06-01"
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}
