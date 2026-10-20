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
 * Universal OpenAI-compatible provider. One implementation reaches Groq, OpenRouter, DeepSeek,
 * Together, Fireworks, a self-hosted vLLM — anything speaking `/chat/completions`. The base URL,
 * model and key all come from [SecureKeyStore] (set via a preset or custom entry), so adding a
 * new backend is configuration, not code. The free presets (Groq / OpenRouter) are the no-billing
 * answer for users who can't add a card.
 */
@Singleton
class OpenAiCompatibleProvider @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
    private val keyStore: SecureKeyStore,
) : LlmProvider {

    override val id = ProviderId.OPENAI_COMPAT

    override fun isConfigured(): Boolean =
        keyStore.hasKey(ProviderId.OPENAI_COMPAT) &&
            !baseUrl().isNullOrBlank() && !model().isNullOrBlank()

    private val rateLimiter = RpmRateLimiter(maxPerMinute = 20)

    override suspend fun generate(prompt: String, systemContext: String?): Result<String> =
        withContext(Dispatchers.IO) {
            val cfg = config() ?: return@withContext Result.failure(notConfigured())
            val body = json.encodeToString(OpenAiRequest.serializer(), buildRequest(cfg.model, prompt, systemContext, false))
                .toRequestBody(JSON_MEDIA)
            runCatching {
                rateLimiter.acquire()
                http.newCall(post(cfg, body)).execute().use { resp ->
                    val payload = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw httpError(resp.code, payload)
                    json.decodeFromString(OpenAiResponse.serializer(), payload).text()
                }
            }
        }

    override fun generateStream(prompt: String, systemContext: String?): Flow<String> = flow {
        val cfg = config() ?: throw notConfigured()
        rateLimiter.acquire()
        val body = json.encodeToString(OpenAiRequest.serializer(), buildRequest(cfg.model, prompt, systemContext, true))
            .toRequestBody(JSON_MEDIA)
        http.newCall(post(cfg, body)).execute().use { resp ->
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
        val cfg = config() ?: return@withContext Result.failure(notConfigured())
        runCatching {
            // A 1-token generation is the most portable validity probe across these backends
            // (not all expose GET /models the same way).
            val body = json.encodeToString(OpenAiRequest.serializer(), buildRequest(cfg.model, "ping", null, false))
                .toRequestBody(JSON_MEDIA)
            http.newCall(post(cfg, body)).execute().use { resp ->
                if (!resp.isSuccessful) throw httpError(resp.code, resp.body?.string().orEmpty())
            }
        }
    }

    private data class Config(val key: String, val baseUrl: String, val model: String)

    private fun baseUrl() = keyStore.compatBaseUrl
    private fun model() = keyStore.compatModel

    private fun config(): Config? {
        val key = keyStore.key(ProviderId.OPENAI_COMPAT) ?: return null
        val url = baseUrl()?.takeIf { it.isNotBlank() } ?: return null
        val model = model()?.takeIf { it.isNotBlank() } ?: return null
        return Config(key, url.trimEnd('/'), model)
    }

    private fun post(cfg: Config, body: okhttp3.RequestBody) = Request.Builder()
        .url("${cfg.baseUrl}/chat/completions")
        .header("Authorization", "Bearer ${cfg.key}")
        // Harmless everywhere; OpenRouter uses it for its app-attribution / free-tier ranking.
        .header("X-Title", "Mnemos")
        .post(body).build()

    private fun buildRequest(model: String, prompt: String, systemContext: String?, stream: Boolean) = OpenAiRequest(
        model = model,
        messages = buildList {
            systemContext?.takeIf { it.isNotBlank() }?.let { add(OpenAiMessage("system", it)) }
            add(OpenAiMessage("user", prompt))
        },
        stream = stream,
        maxTokens = 1024,
    )

    private fun notConfigured() =
        LlmException("Pick a provider (Groq, OpenRouter, DeepSeek or custom) and paste its key in Settings.")

    private fun httpError(code: Int, payload: String): LlmException {
        val retryable = code == 503 || code == 500 || code == 529
        val msg = when (code) {
            401 -> "Key rejected (401) — check the key matches the chosen provider."
            402 -> "Payment required (402) — this provider/model needs credit."
            404 -> "Model not found (404) — check the model name for this provider."
            429 -> "Rate/quota limit hit (429)."
            else -> "Provider HTTP $code"
        }
        return LlmException("$msg ${payload.take(300)}".trim(), retryable = retryable)
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}
