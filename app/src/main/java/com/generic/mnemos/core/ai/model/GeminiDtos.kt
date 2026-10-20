package com.generic.mnemos.core.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire format for the Gemini generateContent API. Only the fields we use are modelled;
// the Json parser ignores unknown keys.

@Serializable
data class GeminiRequest(
    val contents: List<Content>,
    @SerialName("systemInstruction") val systemInstruction: Content? = null,
    @SerialName("generationConfig") val generationConfig: GenerationConfig? = null,
)

@Serializable
data class Content(
    val parts: List<Part>,
    val role: String? = null,
)

@Serializable
data class Part(val text: String)

@Serializable
data class GenerationConfig(
    val temperature: Double? = null,
    @SerialName("maxOutputTokens") val maxOutputTokens: Int? = null,
)

@Serializable
data class GeminiResponse(
    val candidates: List<Candidate> = emptyList(),
    @SerialName("promptFeedback") val promptFeedback: PromptFeedback? = null,
)

@Serializable
data class Candidate(
    val content: Content? = null,
    @SerialName("finishReason") val finishReason: String? = null,
)

@Serializable
data class PromptFeedback(
    @SerialName("blockReason") val blockReason: String? = null,
)

/** Convenience: concatenate all text parts of the first candidate. */
fun GeminiResponse.text(): String =
    candidates.firstOrNull()?.content?.parts?.joinToString("") { it.text } ?: ""
