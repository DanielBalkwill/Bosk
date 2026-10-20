package com.generic.mnemos.core.ai.llm

import com.generic.mnemos.core.ai.RpmRateLimiter
import com.generic.mnemos.core.ai.model.OpenAiMessage
import com.generic.mnemos.core.ai.model.OpenAiRequest
import com.generic.mnemos.core.ai.model.OpenAiResponse
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
 * BYOK OpenAI provider (Chat Completions). Key from [SecureKeyStore], sent only to OpenAI as
 * a Bearer token. Defaults to gpt-4o-mini — cheap, fast, capable; swap [MODEL] to change.
 */
@Singleton
class OpenAiProvider @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
    private val keyStore: SecureKeyStore,
) : LlmProvider {

    override val id = ProviderId.OPENAI
    override fun isConfigured() = keyStore.hasKey(ProviderId.OPENAI)

    private val rateLimiter = RpmRateLimiter(maxPerMinute = 20)

    override suspend fun generate(prompt: String, systemContext: String?): Result<String> =
        withContext(Dispatchers.IO) {
            val key = key() ?: return@withContext Result.failure(LlmException("No OpenAI API key set."))
            val body = json.encodeToString(OpenAiRequest.serializer(), buildRequest(prompt, systemContext, stream = false))
                .toRequestBody(JSON_MEDIA)
            runCatching {
                rateLimiter.acquire()
                val request = post(key, body)
                http.newCall(request).execute().use { resp ->
                    val payload = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw httpError(resp.code, payload)
                    json.decodeFromString(OpenAiResponse.serializer(), payload).text()
                }
            }
        }

    override fun generateStream(prompt: String, systemContext: String?): Flow<String> = flow {
        val key = key() ?: throw LlmException("No OpenAI API key set.")
        rateLimiter.acquire()
        val body = json.encodeToString(OpenAiRequest.serializer(), buildRequest(prompt, systemContext, stream = true))
            .toRequestBody(JSON_MEDIA)
        http.newCall(post(key, body)).execute().use { resp ->
            val source = resp.body?.source() ?: throw LlmException("Empty stream body.")
            if (!resp.isSuccessful) throw httpError(resp.code, resp.body?.string().orEmpty())
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val chunk = line.removePrefix("data:").trim()
                if (chunk.isEmpty() || chunk == "[DONE]") continue
                val piece = runCatching {
                    json.decodeFromString(OpenAiResponse.serializer(), chunk)
                        .choices.firstOrNull()?.delta?.content
                }.getOrNull()
                if (!piece.isNullOrEmpty()) emit(piece)
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun validateKey(): Result<Unit> = withContext(Dispatchers.IO) {
        val key = key() ?: return@withContext Result.failure(LlmException("No OpenAI API key set."))
        runCatching {
            val request = Request.Builder().url("$BASE_URL/models")
                .header("Authorization", "Bearer $key").get().build()
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw httpError(resp.code, resp.body?.string().orEmpty())
            }
        }
    }

    private fun key() = keyStore.key(ProviderId.OPENAI)

    private fun post(key: String, body: okhttp3.RequestBody) = Request.Builder()
        .url("$BASE_URL/chat/completions")
        .header("Authorization", "Bearer $key")
        .post(body).build()

    private fun buildRequest(prompt: String, systemContext: String?, stream: Boolean) = OpenAiRequest(
        model = MODEL,
        messages = buildList {
            systemContext?.takeIf { it.isNotBlank() }?.let { add(OpenAiMessage("system", it)) }
            add(OpenAiMessage("user", prompt))
        },
        stream = stream,
        maxTokens = 1024,
    )

    private fun httpError(code: Int, payload: String): LlmException {
        val retryable = code == 503 || code == 500 || code == 529
        val msg = when (code) {
            401 -> "OpenAI key rejected (401) — check the key."
            429 -> "OpenAI rate/quota limit hit (429). Add billing or wait."
            else -> "OpenAI HTTP $code"
        }
        return LlmException("$msg ${payload.take(300)}".trim(), retryable = retryable)
    }

    companion object {
        const val BASE_URL = "https://api.openai.com/v1"
        const val MODEL = "gpt-4o-mini"
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}
