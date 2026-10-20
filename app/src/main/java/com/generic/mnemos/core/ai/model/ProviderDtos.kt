package com.generic.mnemos.core.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------------------
// OpenAI Chat Completions wire format (api.openai.com/v1/chat/completions). Only modelled
// fields are read; the Json parser ignores unknown keys.
// ---------------------------------------------------------------------------------------

@Serializable
data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val stream: Boolean = false,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val temperature: Double? = null,
)

@Serializable
data class OpenAiMessage(val role: String, val content: String)

@Serializable
data class OpenAiResponse(val choices: List<OpenAiChoice> = emptyList())

@Serializable
data class OpenAiChoice(
    val message: OpenAiMessage? = null,
    val delta: OpenAiDelta? = null,
)

@Serializable
data class OpenAiDelta(val content: String? = null)

/** First choice's full message text (non-streamed). */
fun OpenAiResponse.text(): String = choices.firstOrNull()?.message?.content ?: ""

// ---------------------------------------------------------------------------------------
// Anthropic Messages wire format (api.anthropic.com/v1/messages). The system prompt is a
// top-level field, not a message. Streaming uses typed SSE events (content_block_delta).
// ---------------------------------------------------------------------------------------

@Serializable
data class AnthropicRequest(
    val model: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val messages: List<AnthropicMessage>,
    val system: String? = null,
    val stream: Boolean = false,
)

@Serializable
data class AnthropicMessage(val role: String, val content: String)

@Serializable
data class AnthropicResponse(val content: List<AnthropicContentBlock> = emptyList())

@Serializable
data class AnthropicContentBlock(val type: String? = null, val text: String? = null)

/** Concatenate all text blocks of a non-streamed message. */
fun AnthropicResponse.text(): String =
    content.filter { it.type == null || it.type == "text" }.joinToString("") { it.text ?: "" }

/** A single SSE event in an Anthropic stream — we only care about content_block_delta. */
@Serializable
data class AnthropicStreamEvent(
    val type: String? = null,
    val delta: AnthropicStreamDelta? = null,
)

@Serializable
data class AnthropicStreamDelta(val type: String? = null, val text: String? = null)
