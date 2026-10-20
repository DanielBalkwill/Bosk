package com.generic.mnemos.core.ai

import com.generic.mnemos.core.ai.llm.LlmClient
import com.generic.mnemos.core.graph.GraphRepository
import com.generic.mnemos.core.notes.Note
import com.generic.mnemos.core.notes.NotesRepository
import com.generic.mnemos.core.notes.Redaction
import com.generic.mnemos.core.review.ReviewRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level, per-note AI actions surfaced in the notes editor (CP3). Each composes the
 * BYOK [LlmClient] (Gemini / OpenAI / Claude / on-device Nano) with a [PromptTemplate] and
 * the user's knowledge profile (Mulch pattern). All return [Result] so the UI can show
 * friendly errors (e.g. no API key).
 */
@Singleton
class NoteAiService @Inject constructor(
    private val llm: LlmClient,
    private val prompts: PromptLibrary,
    private val profile: KnowledgeProfileManager,
    private val reviews: ReviewRepository,
    private val graph: GraphRepository,
    private val notes: NotesRepository,
    private val json: Json,
) {
    /** Spoken-style summary of a note. */
    suspend fun summarise(note: Note): Result<String> {
        val prompt = prompts.render(
            PromptTemplate.SUMMARISE_NOTE,
            mapOf("title" to note.title, "body" to Redaction.forAi(note.body)),
        )
        return llm.generate(prompt, profile.currentContextBlock())
    }

    /**
     * Build/edit a note from a free-text instruction — OR answer a question about it. The model
     * first DECIDES: an edit streams the full updated Markdown body (so the editor shows the note
     * rewrite itself live); a question/answer streams a single response beginning with the marker
     * `@answer `, which the editor routes to a response box instead of overwriting the note. The
     * model is told to return ONLY the body or the marker line (no preamble / fences). See
     * [ANSWER_MARKER].
     */
    fun assistStream(note: Note, instruction: String): Flow<String> {
        val current = Redaction.forAi(note.body).ifBlank { "(the note is currently empty)" }
        val prompt = """
            You are helping the user with their study note titled "${note.title.ifBlank { "Untitled" }}".

            Current note body:
            ---
            $current
            ---

            The user's instruction: $instruction

            Decide which of these applies and respond accordingly:
            • If the instruction asks you to WRITE, EDIT, REWRITE, EXPAND, restructure, or otherwise
              CHANGE the note, output ONLY the full, updated note body in clean Markdown. Preserve any
              existing [[wiki-links]] and !links unless told otherwise. No title, preamble, or code fences.
            • If the instruction is a QUESTION or a request that should NOT modify the note (e.g.
              "what does this mean?", "quiz me", "is this correct?"), reply with a SINGLE response that
              begins exactly with "$ANSWER_MARKER" followed by your answer (Markdown allowed).

            ACTUALLY PERFORM the instruction. Never reply by describing or explaining what the
            instruction/command would do — do the work and return the result.
            Output the note body OR the "$ANSWER_MARKER" response — nothing else.
        """.trimIndent()
        return flow { emitAll(llm.generateStream(prompt, profile.currentContextBlock())) }
    }

    /**
     * Analyse a note into a colour-coded glossary of inline annotations (definitions, examples,
     * simplifications, caveats…). Nothing is written to the note — the caller renders these in the
     * client and offers to save an annotated copy. Returns the annotations in document order.
     */
    suspend fun analyze(note: Note): Result<List<NoteAnnotation>> {
        val body = Redaction.forAi(note.body)
        if (body.isBlank()) return Result.success(emptyList())
        val cats = NoteAnnotation.CATEGORIES.joinToString(", ")
        val prompt = """
            Analyse this study note. Walk through it in order and produce a glossary of short
            annotations explaining what is happening at each point (e.g. "defines the key term",
            "worked example", "this sentence is simplifying the idea above", "important caveat").

            For each annotation return:
            • "excerpt": a SHORT, EXACT, verbatim substring copied from the note that the annotation
              refers to (a few words — must appear in the note exactly so it can be located).
            • "category": one of [$cats].
            • "comment": one concise sentence describing what is going on there.

            Note title: ${note.title.ifBlank { "Untitled" }}
            Note body:
            ---
            $body
            ---

            Return ONLY a JSON array like:
            [{"excerpt":"...","category":"...","comment":"..."}]
            8–16 annotations, ordered as they appear in the note. No prose, no code fences.
        """.trimIndent()
        return llm.generate(prompt, profile.currentContextBlock()).mapCatching { raw ->
            val arr = json.parseToJsonElement(extractJson(raw)) as? JsonArray
                ?: error("Model did not return an annotation array.")
            arr.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val excerpt = obj["excerpt"]?.jsonPrimitive?.content?.trim().orEmpty()
                val comment = obj["comment"]?.jsonPrimitive?.content?.trim().orEmpty()
                val category = obj["category"]?.jsonPrimitive?.content?.trim()?.lowercase().orEmpty()
                if (excerpt.isEmpty() || comment.isEmpty()) null
                else NoteAnnotation(excerpt, category.ifBlank { "note" }, comment)
            }
        }
    }

    /**
     * Step 1 of a bulk edit: turn the user's free-text request into a handful of client-side search
     * queries, so we can narrow to the affected notes WITHOUT uploading the whole library. Returns
     * short keyword/phrase queries.
     */
    suspend fun deriveSearchQueries(request: String): Result<List<String>> {
        val prompt = """
            The user wants to bulk-edit some of their study notes with this request:
            "$request"

            List the search queries that would locate the notes this request applies to — short
            single words or phrases that are likely to appear in those notes' titles/bodies/tags.
            Return ONLY a JSON array of 3–6 strings, e.g. ["photosynthesis", "cell wall", "biology"].
        """.trimIndent()
        return llm.generate(prompt).mapCatching { raw ->
            val arr = json.parseToJsonElement(extractJson(raw)) as? JsonArray
                ?: error("Model did not return a query array.")
            arr.mapNotNull { it.jsonPrimitive.content.trim().takeIf { s -> s.isNotEmpty() } }.distinct()
        }
    }

    /**
     * Step 2 of a bulk edit: apply [instruction] to ONE note and return its full updated body.
     * One note per call keeps each request small (global rule #4 — never overload a single call).
     */
    suspend fun editNoteBody(note: Note, instruction: String): Result<String> {
        val current = Redaction.forAi(note.body).ifBlank { "(the note is currently empty)" }
        val prompt = """
            You are editing the study note titled "${note.title.ifBlank { "Untitled" }}".

            Current body:
            ---
            $current
            ---

            Apply this instruction: $instruction

            Return ONLY the full, updated note body in clean Markdown. Preserve any existing
            [[wiki-links]] and !links unless told otherwise. No title, preamble, or code fences.
        """.trimIndent()
        return llm.generate(prompt, profile.currentContextBlock()).map { it.trim() }
    }

    /**
     * Build a structured, time-blocked daily learning roadmap from today's due count + recent topics.
     * Returns 5–7 [RoadmapStep]s parsed from a JSON array (reusing [extractJson] + [json], like
     * [analyze]/[generateCards]). The Home card renders each step colour-coded with a bold time.
     */
    suspend fun generateRoadmap(dueCount: Int, topics: List<String>): Result<List<RoadmapStep>> {
        val topicList = topics.filter { it.isNotBlank() }.joinToString(", ").ifBlank { "no notes yet" }
        val prompt = """
            Create a concise, motivating daily learning roadmap for today. The learner has $dueCount
            flashcard(s) due for review and these recent topics: $topicList.

            Produce 5–7 time-blocked steps covering: reviewing due cards, filling knowledge gaps,
            and exploring one new topic. For each step return:
            • "time": a short time block, e.g. "09:00–09:30" or "Morning".
            • "title": a short imperative of what to do (max ~8 words).
            • "detail": one concise sentence of elaboration.
            • "notes": an array of 0–3 topic/note titles (from the recent topics where relevant).

            Return ONLY a JSON array like:
            [{"time":"09:00–09:30","title":"Review due cards","detail":"...","notes":["..."]}]
            No prose, no code fences.
        """.trimIndent()
        return llm.generate(prompt, profile.currentContextBlock()).mapCatching { raw ->
            val arr = json.parseToJsonElement(extractJson(raw)) as? JsonArray
                ?: error("Model did not return a roadmap array.")
            arr.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val time = obj["time"]?.jsonPrimitive?.content?.trim().orEmpty()
                val title = obj["title"]?.jsonPrimitive?.content?.trim().orEmpty()
                val detail = obj["detail"]?.jsonPrimitive?.content?.trim().orEmpty()
                val noteRefs = (obj["notes"] as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.content.trim().takeIf { s -> s.isNotEmpty() } }
                    ?: emptyList()
                if (title.isEmpty()) null else RoadmapStep(time, title, detail, noteRefs)
            }
        }
    }

    /** Explain a concept tailored to the user's profile. */
    suspend fun explain(concept: String): Result<String> {
        val prompt = prompts.render(PromptTemplate.EXPLAIN_CONCEPT, mapOf("concept" to concept))
        return llm.generate(prompt, profile.currentContextBlock())
    }

    /** Generate flashcards from a note and persist them. Returns the number created. */
    suspend fun generateCards(note: Note): Result<Int> {
        val prompt = prompts.render(
            PromptTemplate.GENERATE_CARD,
            mapOf("title" to note.title, "body" to Redaction.forAi(note.body)),
        )
        return llm.generate(prompt, profile.currentContextBlock()).mapCatching { raw ->
            val arr = json.parseToJsonElement(extractJson(raw)) as? JsonArray
                ?: error("Model did not return a card array.")
            var created = 0
            arr.forEach { el ->
                val obj = el as? JsonObject ?: return@forEach
                val front = obj["front"]?.jsonPrimitive?.content?.trim().orEmpty()
                val back = obj["back"]?.jsonPrimitive?.content?.trim().orEmpty()
                if (front.isNotEmpty() && back.isNotEmpty()) {
                    reviews.addCard(note.id, front, back, aiGenerated = true)
                    created++
                }
            }
            created
        }
    }

    /**
     * Compare this note to up to [maxCandidates] others; create AI-suggested edges where
     * the model judges them connected. Returns the number of edges created. Capped to stay
     * within the free-tier RPM budget.
     */
    suspend fun findConnections(note: Note, maxCandidates: Int = 4): Result<Int> = runCatching {
        val sourceNode = graph.ensureNodeForNote(note.id, note.title)
        val others = notes.observeNotes().first()
            .filter { it.id != note.id }
            .take(maxCandidates)

        // Fetch the profile context once (not per call).
        val systemContext = profile.currentContextBlock()

        var created = 0
        for (other in others) {
            val prompt = prompts.render(
                PromptTemplate.CONNECT_NODES,
                mapOf(
                    "titleA" to note.title, "bodyA" to Redaction.forAi(note.body),
                    "titleB" to other.title, "bodyB" to Redaction.forAi(other.body),
                ),
            )
            val result = llm.generate(prompt, systemContext)
            // Stop immediately on a failure (e.g. rate limit / quota) — don't keep hammering
            // the API for the remaining candidates. Surface what we already created.
            val resp = result.getOrElse { throw it }
            val obj = runCatching { json.parseToJsonElement(extractJson(resp)).jsonObject }.getOrNull()
                ?: continue
            val connected = runCatching { obj["connected"]?.jsonPrimitive?.boolean == true }.getOrDefault(false)
            if (!connected) continue
            val strength = runCatching { obj["strength"]?.jsonPrimitive?.float ?: 0.5f }.getOrDefault(0.5f)
            val label = obj["relationship"]?.jsonPrimitive?.content
            val targetNode = graph.ensureNodeForNote(other.id, other.title)
            graph.addEdge(sourceNode, targetNode, label, strength, aiGenerated = true)
            created++
        }
        created
    }

    /** Strip ```json fences / surrounding prose to get at the JSON payload. */
    private fun extractJson(raw: String): String {
        val s = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = s.indexOfFirst { it == '{' || it == '[' }
        val end = s.indexOfLast { it == '}' || it == ']' }
        return if (start in 0..end) s.substring(start, end + 1) else s
    }

    companion object {
        /** Prefix the model emits (via [assistStream]) when it answers rather than edits the note. */
        const val ANSWER_MARKER = "@answer "
    }
}

/** One colour-coded annotation over a note, produced by [NoteAiService.analyze]. Client-only. */
data class NoteAnnotation(
    /** Verbatim slice of the note body this annotation refers to (used to locate it). */
    val excerpt: String,
    /** One of [CATEGORIES] (falls back to "note"); drives the highlight colour. */
    val category: String,
    /** One-sentence description of what is happening at that point. */
    val comment: String,
) {
    companion object {
        val CATEGORIES = listOf(
            "definition", "example", "key-point", "simplify", "caveat", "question", "context",
        )
    }
}
