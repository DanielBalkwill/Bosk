package app.bosk.core.ai.llm

import app.bosk.core.ai.RpmRateLimiter
import app.bosk.core.ai.model.Content
import app.bosk.core.ai.model.GeminiRequest
import app.bosk.core.ai.model.GeminiResponse
import app.bosk.core.ai.model.Part
import app.bosk.core.ai.model.text
import app.bosk.core.auth.SecureKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 * Direct-to-Gemini BYOK provider (no proxy). The key is read from [SecureKeyStore] per call
 * and only ever sent to [BASE_URL]. Retries 500/503 with backoff and caps RPM client-side.
 * 429 is deliberately NOT retried — RpmRateLimiter already paces us, so a 429 means the
 * actual daily quota is gone and retrying only burns more of it.
 */
@Singleton
class GeminiProvider @Inject constructor(
    private val http: OkHttpClient,
    private val json: Json,
    private val keyStore: SecureKeyStore,
) : LlmProvider {

    override val id = ProviderId.GEMINI
    override fun isConfigured() = keyStore.hasKey(ProviderId.GEMINI)

    private val rateLimiter = RpmRateLimiter(maxPerMinute = 15)

    override suspend fun generate(prompt: String, systemContext: String?): Result<String> =
        withContext(Dispatchers.IO) {
            val key = key() ?: return@withContext Result.failure(LlmException("No Gemini API key set."))
            val body = json.encodeToString(GeminiRequest.serializer(), buildRequest(prompt, systemContext))
                .toRequestBody(JSON_MEDIA)
            val url = "$BASE_URL/$MODEL:generateContent?key=$key"
            runCatching {
                withRetry {
                    val request = Request.Builder().url(url).post(body).build()
                    http.newCall(request).execute().use { resp ->
                        val payload = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) throw httpError(resp.code, payload)
                        val parsed = json.decodeFromString(GeminiResponse.serializer(), payload)
                        parsed.promptFeedback?.blockReason?.let {
                            throw LlmException("Blocked by safety filter: $it")
                        }
                        parsed.text()
                    }
                }
            }
        }

    override fun generateStream(prompt: String, systemContext: String?): Flow<String> = flow {
        val key = key() ?: throw LlmException("No Gemini API key set.")
        rateLimiter.acquire()
        val body = json.encodeToString(GeminiRequest.serializer(), buildRequest(prompt, systemContext))
            .toRequestBody(JSON_MEDIA)
        val url = "$BASE_URL/$MODEL:streamGenerateContent?alt=sse&key=$key"
        val request = Request.Builder().url(url).post(body).build()
        http.newCall(request).execute().use { resp ->
            val source = resp.body?.source() ?: throw LlmException("Empty stream body.")
            if (!resp.isSuccessful) throw httpError(resp.code, resp.body?.string().orEmpty())
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val chunk = line.removePrefix("data:").trim()
                if (chunk.isEmpty() || chunk == "[DONE]") continue
                val piece = runCatching {
                    json.decodeFromString(GeminiResponse.serializer(), chunk).text()
                }.getOrNull()
                if (!piece.isNullOrEmpty()) emit(piece)
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun validateKey(): Result<Unit> = withContext(Dispatchers.IO) {
        val key = key() ?: return@withContext Result.failure(LlmException("No Gemini API key set."))
        runCatching {
            val request = Request.Builder().url("$BASE_URL?key=$key").get().build()
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw httpError(resp.code, resp.body?.string().orEmpty())
            }
        }
    }

    private fun key() = keyStore.key(ProviderId.GEMINI)

    private fun buildRequest(prompt: String, systemContext: String?) = GeminiRequest(
        contents = listOf(Content(parts = listOf(Part(prompt)), role = "user")),
        systemInstruction = systemContext?.takeIf { it.isNotBlank() }
            ?.let { Content(parts = listOf(Part(it))) },
    )

    private suspend fun <T> withRetry(maxAttempts: Int = 4, block: suspend () -> T): T {
        var attempt = 0
        var backoff = 500L
        while (true) {
            try {
                rateLimiter.acquire()
                return block()
            } catch (e: LlmException) {
                attempt++
                if (!e.retryable || attempt >= maxAttempts) throw e
                delay(backoff + (0..250).random())
                backoff *= 2
            }
        }
    }

    private fun httpError(code: Int, payload: String): LlmException {
        val retryable = code == 503 || code == 500
        val msg = when (code) {
            400 -> "Bad request (check the API key / payload)."
            403 -> "Access denied — is the Gemini API enabled for this key?"
            429 -> "Rate limit hit (free tier is 15 RPM / 1500 per day)."
            else -> "Gemini HTTP $code"
        }
        return LlmException("$msg ${payload.take(300)}".trim(), retryable = retryable)
    }

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        const val MODEL = "gemini-2.0-flash"
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}
