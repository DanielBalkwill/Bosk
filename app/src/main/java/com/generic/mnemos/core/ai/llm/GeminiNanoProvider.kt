package com.generic.mnemos.core.ai.llm

import android.content.Context
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device free tier: Gemini Nano via Google AICore. Runs entirely on the phone — no key,
 * no network, no per-call cost, and notes never leave the device. Only available on AICore-
 * capable hardware (e.g. Pixel 8+/Samsung S24+ with the AICore system app); elsewhere every
 * call fails fast with a friendly "not available on this device" message.
 *
 * The router only routes here when the user *explicitly* selects it, so availability errors
 * surface as a deliberate choice rather than an accidental default.
 */
@Singleton
class GeminiNanoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : LlmProvider {

    override val id = ProviderId.NANO

    // Availability can only be confirmed by trying to start the engine — so we report
    // "configured" optimistically and let generate()/stream() surface the real verdict.
    override fun isConfigured() = true

    private val mutex = Mutex()
    @Volatile private var model: GenerativeModel? = null

    private suspend fun model(): GenerativeModel = mutex.withLock {
        model ?: run {
            val cfg = generationConfig {
                context = this@GeminiNanoProvider.context
                temperature = 0.2f
                topK = 16
                maxOutputTokens = 512
            }
            val m = GenerativeModel(generationConfig = cfg)
            m.prepareInferenceEngine() // throws if the device/AICore can't run Nano
            model = m
            m
        }
    }

    override suspend fun generate(prompt: String, systemContext: String?): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val full = compose(prompt, systemContext)
                model().generateContent(full).text.orEmpty()
            }.recoverCatching { throw unavailable(it) }
        }

    override fun generateStream(prompt: String, systemContext: String?): Flow<String> = flow {
        val m = model()
        emitAll(m.generateContentStream(compose(prompt, systemContext)).map { it.text.orEmpty() })
    }.catch { throw unavailable(it) }.flowOn(Dispatchers.IO)

    override suspend fun validateKey(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { model() }.map { }.recoverCatching { throw unavailable(it) }
    }

    // Nano has no system-instruction slot, so we fold the profile context into the prompt.
    private fun compose(prompt: String, systemContext: String?): String =
        if (systemContext.isNullOrBlank()) prompt else "$systemContext\n\n$prompt"

    private fun unavailable(cause: Throwable): LlmException {
        if (cause is LlmException) return cause
        return LlmException(
            "On-device AI (Gemini Nano) isn't available on this device. " +
                "Add a Gemini, OpenAI or Claude key in Settings instead. " +
                "(${cause.message ?: cause.javaClass.simpleName})",
        )
    }
}
