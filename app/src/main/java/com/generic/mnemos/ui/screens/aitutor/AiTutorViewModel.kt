package com.generic.mnemos.ui.screens.aitutor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.generic.mnemos.core.ai.DailyRoadmap
import com.generic.mnemos.core.ai.KnowledgeProfileManager
import com.generic.mnemos.core.ai.RoadmapStep
import com.generic.mnemos.core.ai.llm.LlmClient
import com.generic.mnemos.core.commands.CommandNote
import com.generic.mnemos.core.commands.CommandRegistry
import com.generic.mnemos.core.commands.CommandResult
import com.generic.mnemos.core.notes.CompilerStore
import com.generic.mnemos.core.notes.Note
import com.generic.mnemos.core.notes.NotesRepository
import com.generic.mnemos.core.notes.Redaction
import com.generic.mnemos.core.settings.SettingsRepository
import com.generic.mnemos.core.srs.Rating
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class TutorRole { USER, TUTOR, SYSTEM }

/** One turn in the tutor conversation. */
data class TutorMessage(val role: TutorRole, val text: String)

/** A quick-action the learner can fire instead of typing (RemNote-style study prompts). */
enum class TutorAction(val label: String) {
    QUIZ("Quiz me"),
    EXPLAIN("Explain"),
    CHECK("Check understanding"),
    PLAN("Walk my roadmap"),
}

/** One quiz question. [reviewId] non-null = a real flashcard, so grading updates its FSRS schedule. */
data class QuizQuestion(
    val prompt: String,
    val expected: String,
    val reviewId: Long? = null,
    val noteId: Long? = null,
)

/** Live quiz progress: the question list, position, score, and what was missed. */
data class QuizState(
    val questions: List<QuizQuestion>,
    val index: Int = 0,
    val correct: Int = 0,
    val partial: Int = 0,
    val wrong: Int = 0,
    val missed: List<QuizQuestion> = emptyList(),
) {
    val current: QuizQuestion? get() = questions.getOrNull(index)
    val total: Int get() = questions.size
}

/**
 * The AI Tutor: a conversational, notes-grounded study companion (a RemNote-style AI tutor). It
 * explains, quizzes, and checks understanding using the learner's own material — the notes queued
 * in the [CompilerStore] and the saved Daily Roadmap — which the learner attaches as context from
 * the "Context" sheet. The knowledge profile is always prepended (Mulch pattern).
 */
@HiltViewModel
class AiTutorViewModel @Inject constructor(
    private val llm: LlmClient,
    private val profile: KnowledgeProfileManager,
    private val notes: NotesRepository,
    private val compiler: CompilerStore,
    private val settings: SettingsRepository,
    private val commandRegistry: CommandRegistry,
    private val session: TutorSessionStore,
    private val noteAi: com.generic.mnemos.core.ai.NoteAiService,
    private val reviews: com.generic.mnemos.core.review.ReviewRepository,
    private val json: kotlinx.serialization.json.Json,
) : ViewModel() {

    /** Slash commands offered in the tutor's palette (driven by the editable command note). */
    val commands: StateFlow<List<CommandNote.CommandDoc>> =
        commandRegistry.commands.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Conversation + attachments live in the app-wide TutorSessionStore, so leaving the tutor
    // and coming back RESUMES the session instead of wiping it.
    val messages: StateFlow<List<TutorMessage>> = session.messages

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _contextOpen = MutableStateFlow(false)
    val contextOpen: StateFlow<Boolean> = _contextOpen.asStateFlow()

    /** The whole vault, title-sorted — browsable in the Context sheet (any note is attachable). */
    val vaultNotes: StateFlow<List<Note>> =
        notes.observeNotes()
            .map { list -> list.sortedBy { it.title.lowercase() } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Compiler-queued notes resolved to full notes (the tutor's attachable context). */
    val compilerNotes: StateFlow<List<Note>> =
        combine(compiler.items, notes.observeNotes()) { items, all ->
            val byId = all.associateBy { it.id }
            items.mapNotNull { byId[it.noteId] }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Today's saved roadmap (null if none / from a previous day). */
    val savedRoadmap: StateFlow<DailyRoadmap?> =
        combine(settings.dailyRoadmapJson, settings.dailyRoadmapEpochDay) { json, _ ->
            DailyRoadmap.decode(json)?.takeIf { it.isForDay(LocalDate.now().toEpochDay()) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Note ids the learner has attached to the conversation context. */
    val attachedNoteIds: StateFlow<Set<Long>> = session.attachedNoteIds

    /** Whether the daily roadmap is pinned into the conversation context. */
    val roadmapAttached: StateFlow<Boolean> = session.roadmapAttached

    // ── Attach ANY note: a search box in the Context sheet ────────────────────────────────────
    private val _noteQuery = MutableStateFlow("")
    val noteQuery: StateFlow<String> = _noteQuery.asStateFlow()
    fun setNoteQuery(q: String) { _noteQuery.value = q }

    /** Title-matched candidates for the Context sheet's search box (top 8). */
    val searchResults: StateFlow<List<Note>> =
        combine(_noteQuery, vaultNotes) { q, all ->
            val query = q.trim()
            if (query.isBlank()) emptyList()
            else all.filter { it.title.contains(query, ignoreCase = true) }.take(8)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The attached notes resolved to full notes (for listing in the sheet + grounding prompts). */
    val attachedNotes: StateFlow<List<Note>> =
        combine(session.attachedNoteIds, vaultNotes) { ids, all ->
            all.filter { it.id in ids }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * A note loaded directly into the conversation (e.g. via a swipe-right from the notes list),
     * independent of the Compiler queue. Always grounds the prompt while set.
     */
    private val _preloadedNote = MutableStateFlow<Note?>(null)
    val preloadedNote: StateFlow<Note?> = _preloadedNote.asStateFlow()

    private var started = false

    /**
     * Called once from the screen. If [withRoadmap], pin the roadmap and open on it. If [autoNoteId]
     * is a real id, load that note as context and immediately kick off a tutor inquiry into it.
     */
    fun start(withRoadmap: Boolean, autoNoteId: Long = -1L) {
        if (started) return
        started = true
        viewModelScope.launch {
            val rm = DailyRoadmap.decode(settings.dailyRoadmapJson.first())
                ?.takeIf { it.isForDay(LocalDate.now().toEpochDay()) }
            val hasRoadmap = withRoadmap && rm != null
            if (hasRoadmap) session.setRoadmapAttached(true)

            val autoNote = if (autoNoteId > 0) runCatching { notes.get(autoNoteId) }.getOrNull() else null
            if (autoNote != null) {
                _preloadedNote.value = autoNote
                session.append(
                    TutorMessage(
                        TutorRole.TUTOR,
                        "Let's dig into \"${autoNote.title.ifBlank { "this note" }}\" — one moment…",
                    ),
                )
                kickoffInquiry(autoNote)
                return@launch
            }

            // A previous session is still in the store → resume it silently, don't re-greet.
            if (session.messages.value.isNotEmpty()) return@launch

            val greeting = if (hasRoadmap) {
                "Here's your roadmap for today — let's work through it together. Tell me which block " +
                    "you want to start with, or say \"begin\" and I'll guide you step by step."
            } else {
                "Hi — I'm your study tutor. Attach notes or your roadmap from the Context button, then " +
                    "ask me to explain something or check your understanding. \"Quiz me\" runs a real " +
                    "graded quiz — due flashcards first (your review schedule updates as you answer), " +
                    "with hints when you're stuck."
            }
            session.append(TutorMessage(TutorRole.TUTOR, greeting))
        }
    }

    /** Wipe the conversation and start fresh (top-bar "New session"). */
    fun clearSession() {
        session.clear()
        session.append(TutorMessage(TutorRole.TUTOR, "Fresh start. What would you like to study?"))
    }

    /** Auto-start: explain the just-opened note, then check understanding — no user turn needed. */
    private fun kickoffInquiry(note: Note) {
        _busy.value = true
        viewModelScope.launch {
            val prompt = buildString {
                appendLine("The learner just opened this note to study it:")
                appendLine("Title: ${note.title.ifBlank { "Untitled" }}")
                appendLine("Content: ${Redaction.forAi(note.body).take(2000)}")
                appendLine()
                append(
                    "Give a clear, engaging explanation of the key ideas in this note, then ask me one " +
                        "question to check my understanding. Keep it concise and conversational.",
                )
            }
            streamTutorReply(prompt)
            _busy.value = false
        }
    }

    /**
     * Stream a tutor reply into the last bubble so the answer appears live (word by word) instead
     * of after a long blank "thinking…" wait. Failures remove the empty bubble and post a SYSTEM line.
     */
    private suspend fun streamTutorReply(prompt: String) {
        val systemContext = buildString {
            appendLine(TUTOR_PERSONA)
            append(runCatching { profile.currentContextBlock() }.getOrDefault(""))
        }
        session.append(TutorMessage(TutorRole.TUTOR, ""))
        val buf = StringBuilder()
        try {
            llm.generateStream(prompt, systemContext).collect { chunk ->
                buf.append(chunk)
                session.updateLast(buf.toString().trimStart())
            }
            if (buf.isBlank()) session.updateLast("(no reply)")
        } catch (e: Exception) {
            session.dropLastIfBlank()
            session.append(TutorMessage(TutorRole.SYSTEM, e.message ?: "Couldn't reach the AI."))
        }
    }

    // ── Turn a tutor reply into lasting material ───────────────────────────────────────────────

    /** Save a tutor reply as a new note (title from its first words, tagged #tutor). */
    fun saveReplyToNote(text: String) {
        val body = text.trim()
        if (body.isBlank()) return
        viewModelScope.launch {
            try {
                val title = "Tutor: " + body.substringBefore('\n').take(48).trim().ifBlank { "explanation" }
                notes.upsert(Note(title = title, body = body, tags = listOf("tutor")))
                session.append(TutorMessage(TutorRole.SYSTEM, "Saved to notes as \"$title\"."))
            } catch (e: Exception) {
                session.append(TutorMessage(TutorRole.SYSTEM, e.message ?: "Couldn't save the note."))
            }
        }
    }

    /** Generate flashcards from a tutor reply, collected under a "Tutor Cards" holder deck. */
    fun cardsFromReply(text: String) {
        val body = text.trim()
        if (body.isBlank() || _busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val holder = notes.observeNotes().first().firstOrNull { it.title.equals("Tutor Cards", ignoreCase = true) }
                    ?: Note(title = "Tutor Cards", body = "Flashcards created from AI Tutor replies.", tags = listOf("tutor"))
                        .let { it.copy(id = notes.upsert(it)) }
                val made = noteAi.generateCards(holder.copy(body = body)).getOrThrow()
                session.append(
                    TutorMessage(TutorRole.SYSTEM, "Created $made flashcard${if (made == 1) "" else "s"} → deck \"Tutor Cards\"."),
                )
            } catch (e: Exception) {
                session.append(TutorMessage(TutorRole.SYSTEM, e.message ?: "Couldn't create flashcards."))
            }
            _busy.value = false
        }
    }

    fun openContext() { _contextOpen.value = true }
    fun closeContext() { _contextOpen.value = false }

    fun toggleNoteAttached(id: Long) = session.toggleNote(id)

    fun toggleRoadmapAttached() = session.setRoadmapAttached(!session.roadmapAttached.value)

    fun action(action: TutorAction) {
        when (action) {
            // Quiz is a real, structured session now (graded answers, score, SRS updates) —
            // not just a prompt asking the model to behave like one.
            TutorAction.QUIZ -> startQuiz()
            TutorAction.EXPLAIN ->
                send("Explain the key ideas in the attached material clearly, then point out what I should make sure I understand.")
            TutorAction.CHECK ->
                send("Check my understanding: ask me 2–3 questions that reveal whether I really grasp the attached material.")
            TutorAction.PLAN ->
                send("Walk me through today's roadmap one step at a time, starting with the first block.")
        }
    }

    fun send(rawInput: String) {
        val input = rawInput.trim()
        if (input.isEmpty() || _busy.value) return
        // Mid-quiz, plain text is the learner's ANSWER (with a few control words); slash
        // commands still pass through to the normal engine.
        if (_quiz.value != null && !input.startsWith("/")) {
            session.append(TutorMessage(TutorRole.USER, input))
            when (input.lowercase()) {
                "stop", "quit", "end" -> stopQuiz()
                "skip" -> quizSkip()
                "hint" -> quizHint()
                else -> {
                    _busy.value = true
                    viewModelScope.launch {
                        try { gradeQuizAnswer(input) } finally { _busy.value = false }
                    }
                }
            }
            return
        }
        session.append(TutorMessage(TutorRole.USER, input))
        _busy.value = true
        viewModelScope.launch {
            when (val result = commandRegistry.process(input)) {
                is CommandResult.Clear ->
                    session.setMessages(listOf(TutorMessage(TutorRole.TUTOR, "Cleared. What would you like to study?")))
                is CommandResult.Help ->
                    session.append(TutorMessage(TutorRole.SYSTEM, result.text))
                is CommandResult.Prompt -> streamTutorReply(buildPrompt(result.text))
            }
            _busy.value = false
        }
    }

    // ── Structured quiz mode: graded answers, hints, real FSRS updates, cards from misses ─────

    private val _quiz = MutableStateFlow<QuizState?>(null)
    val quiz: StateFlow<QuizState?> = _quiz.asStateFlow()

    /** Misses from the last finished quiz — enables the "Cards from misses" chip. */
    private val _missedAvailable = MutableStateFlow(false)
    val missedAvailable: StateFlow<Boolean> = _missedAvailable.asStateFlow()
    private var lastMissed: List<QuizQuestion> = emptyList()

    /**
     * Start a quiz: due flashcards first (answering updates their REAL review schedule), else
     * AI-generated questions from the attached/compiler material.
     */
    fun startQuiz() {
        if (_busy.value || _quiz.value != null) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val due = runCatching { reviews.dueCards(QUIZ_LENGTH) }.getOrDefault(emptyList())
                val questions =
                    if (due.isNotEmpty()) due.map { QuizQuestion(it.front, it.back, reviewId = it.id, noteId = it.noteId) }
                    else generateQuizQuestions()
                if (questions.isEmpty()) {
                    session.append(
                        TutorMessage(
                            TutorRole.SYSTEM,
                            "Nothing to quiz on yet — attach notes from the Context sheet or create some flashcards, then try again.",
                        ),
                    )
                    return@launch
                }
                _quiz.value = QuizState(questions = questions)
                val source = if (due.isNotEmpty()) "your due flashcards — answers update their real review schedule"
                else "your attached notes"
                session.append(
                    TutorMessage(
                        TutorRole.TUTOR,
                        "Quiz time: ${questions.size} question${if (questions.size == 1) "" else "s"} from $source. " +
                            "Answer in your own words. Say \"hint\" for a nudge, \"skip\" to move on, \"stop\" to end.",
                    ),
                )
                postCurrentQuestion()
            } finally {
                _busy.value = false
            }
        }
    }

    /** End the quiz early (or naturally) with a score summary. */
    fun stopQuiz() = finishQuiz()

    /** One short nudge on the current question, without revealing the answer. */
    fun quizHint() {
        val cur = _quiz.value?.current ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            val prompt = "The learner asked for a hint on this quiz question. Give ONE short nudge " +
                "(max 1 sentence) WITHOUT revealing the answer.\n" +
                "Question: ${cur.prompt}\nAnswer (do not reveal): ${cur.expected}"
            llm.generate(prompt).fold(
                onSuccess = { session.append(TutorMessage(TutorRole.TUTOR, "Hint: ${it.trim()}")) },
                onFailure = { session.append(TutorMessage(TutorRole.SYSTEM, it.message ?: "Couldn't fetch a hint.")) },
            )
            _busy.value = false
        }
    }

    /** Skip = didn't know it: reveal the answer, count a miss, lapse the real card if there is one. */
    fun quizSkip() {
        val q = _quiz.value ?: return
        val cur = q.current ?: return
        session.append(TutorMessage(TutorRole.TUTOR, "Skipped — the answer was: ${cur.expected}"))
        if (cur.reviewId != null) {
            viewModelScope.launch { runCatching { reviews.recordReview(cur.reviewId, Rating.AGAIN) } }
        }
        advanceQuiz(q.copy(index = q.index + 1, wrong = q.wrong + 1, missed = q.missed + cur))
    }

    private fun postCurrentQuestion() {
        val q = _quiz.value ?: return
        val cur = q.current ?: return
        session.append(TutorMessage(TutorRole.TUTOR, "**Question ${q.index + 1}/${q.total}:** ${cur.prompt}"))
    }

    /** Grade the learner's free-text answer via the model (strict JSON), score it, update FSRS. */
    private suspend fun gradeQuizAnswer(answer: String) {
        val quizState = _quiz.value ?: return
        val cur = quizState.current ?: return
        val prompt = buildString {
            appendLine("Grade this quiz answer strictly but fairly (meaning matters, wording doesn't).")
            appendLine("Question: ${cur.prompt}")
            appendLine("Expected answer: ${cur.expected}")
            appendLine("Learner's answer: $answer")
            append("""Reply with ONLY this JSON: {"verdict":"correct|partial|wrong","feedback":"<max 2 sentences>","rating":"again|hard|good|easy"}""")
        }
        val reply = llm.generate(prompt).getOrElse { e ->
            session.append(TutorMessage(TutorRole.SYSTEM, e.message ?: "Couldn't grade that — send your answer again."))
            return
        }
        val obj = runCatching {
            json.parseToJsonElement(reply.substring(reply.indexOf('{'), reply.lastIndexOf('}') + 1)).jsonObject
        }.getOrNull()
        val verdict = when (obj?.get("verdict")?.jsonPrimitive?.content?.lowercase()) {
            "correct" -> "correct"
            "wrong" -> "wrong"
            else -> "partial"
        }
        val feedback = obj?.get("feedback")?.jsonPrimitive?.content ?: reply.trim().take(240)
        val mark = when (verdict) {
            "correct" -> "✓ Correct!"
            "wrong" -> "✗ Not quite."
            else -> "◐ Partly there."
        }
        session.append(TutorMessage(TutorRole.TUTOR, "$mark $feedback"))
        // Real cards get a real FSRS review from the grade.
        if (cur.reviewId != null) {
            val rating = when (obj?.get("rating")?.jsonPrimitive?.content?.lowercase()) {
                "again" -> Rating.AGAIN
                "hard" -> Rating.HARD
                "easy" -> Rating.EASY
                "good" -> Rating.GOOD
                else -> when (verdict) { "correct" -> Rating.GOOD; "wrong" -> Rating.AGAIN; else -> Rating.HARD }
            }
            runCatching { reviews.recordReview(cur.reviewId, rating) }
        }
        advanceQuiz(
            quizState.copy(
                index = quizState.index + 1,
                correct = quizState.correct + if (verdict == "correct") 1 else 0,
                partial = quizState.partial + if (verdict == "partial") 1 else 0,
                wrong = quizState.wrong + if (verdict == "wrong") 1 else 0,
                missed = if (verdict == "wrong") quizState.missed + cur else quizState.missed,
            ),
        )
    }

    private fun advanceQuiz(next: QuizState) {
        _quiz.value = next
        if (next.current == null) finishQuiz() else postCurrentQuestion()
    }

    private fun finishQuiz() {
        val q = _quiz.value ?: return
        _quiz.value = null
        lastMissed = q.missed
        _missedAvailable.value = q.missed.isNotEmpty()
        val answered = q.correct + q.partial + q.wrong
        val summary = buildString {
            append("**Quiz done!** Score: ${q.correct}/$answered correct")
            if (q.partial > 0) append(" (plus ${q.partial} partly right)")
            append(".")
            when {
                q.missed.isNotEmpty() -> {
                    append("\n\nWorth another look:\n")
                    q.missed.take(5).forEach { append("• ${it.prompt.take(80)}\n") }
                    append("\nTap \"Cards from misses\" and I'll turn these into flashcards.")
                }
                answered > 0 -> append(" Clean sweep — nice work!")
            }
        }
        session.append(TutorMessage(TutorRole.TUTOR, summary))
    }

    /** Turn the last quiz's missed questions into flashcards (real cards already got a lapse). */
    fun cardsFromMisses() {
        if (lastMissed.isEmpty() || _busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                var made = 0
                var holderId: Long? = null
                suspend fun holder(): Long = holderId ?: run {
                    val existing = notes.observeNotes().first()
                        .firstOrNull { it.title.equals("Tutor Cards", ignoreCase = true) }
                    (existing?.id ?: notes.upsert(
                        Note(title = "Tutor Cards", body = "Flashcards created from AI Tutor sessions.", tags = listOf("tutor")),
                    )).also { holderId = it }
                }
                lastMissed.forEach { m ->
                    if (m.reviewId != null) return@forEach // already a real card; its schedule updated
                    reviews.addCard(m.noteId ?: holder(), m.prompt, m.expected, aiGenerated = true)
                    made++
                }
                session.append(
                    TutorMessage(
                        TutorRole.SYSTEM,
                        if (made == 0) "Your misses were real flashcards — their schedules are already updated for another go."
                        else "Created $made flashcard${if (made == 1) "" else "s"} from your misses — see Practise.",
                    ),
                )
                lastMissed = emptyList()
                _missedAvailable.value = false
            } catch (e: Exception) {
                session.append(TutorMessage(TutorRole.SYSTEM, e.message ?: "Couldn't create the cards."))
            }
            _busy.value = false
        }
    }

    /** AI-generated questions from the attached (or compiler) notes, as strict JSON. */
    private suspend fun generateQuizQuestions(): List<QuizQuestion> {
        val ids = session.attachedNoteIds.value
        val all = notes.observeNotes().first()
        val attached = all.filter { it.id in ids }.toMutableList()
        _preloadedNote.value?.let { pn -> if (attached.none { it.id == pn.id }) attached.add(0, pn) }
        if (attached.isEmpty()) {
            val byId = all.associateBy { it.id }
            attached.addAll(compiler.items.value.mapNotNull { byId[it.noteId] }.take(3))
        }
        if (attached.isEmpty()) return emptyList()
        val prompt = buildString {
            appendLine("Create $QUIZ_LENGTH short quiz questions from this material. Mix recall and understanding.")
            appendLine("""Reply with ONLY a JSON array: [{"q":"<question>","a":"<ideal short answer>"}]""")
            appendLine("Material:")
            attached.take(4).forEach { n ->
                appendLine("• ${n.title.ifBlank { "Untitled" }}: ${Redaction.forAi(n.body).take(1200)}")
            }
        }
        val reply = llm.generate(prompt).getOrNull() ?: return emptyList()
        return runCatching {
            val arr = json.parseToJsonElement(
                reply.substring(reply.indexOf('['), reply.lastIndexOf(']') + 1),
            ) as JsonArray
            arr.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val q = o["q"]?.jsonPrimitive?.content?.trim().orEmpty()
                val a = o["a"]?.jsonPrimitive?.content?.trim().orEmpty()
                if (q.isBlank() || a.isBlank()) null
                else QuizQuestion(prompt = q, expected = a, noteId = attached.firstOrNull()?.id)
            }
        }.getOrDefault(emptyList())
    }

    /** Grounded prompt = attached notes + roadmap + recent chat history + the new user turn. */
    private suspend fun buildPrompt(input: String): String = buildString {
        // Attached ids resolve against ALL notes (attachable from Compiler or the search box).
        // Fetched fresh here — the derived StateFlows only compute while the UI subscribes them.
        val ids = session.attachedNoteIds.value
        val attached = notes.observeNotes().first().filter { it.id in ids }.toMutableList()
        // A swipe-right preloaded note is always part of the grounding (unless already attached).
        _preloadedNote.value?.let { pn -> if (attached.none { it.id == pn.id }) attached.add(0, pn) }
        if (attached.isNotEmpty()) {
            appendLine("The learner attached these notes as study material:")
            attached.forEach { n ->
                appendLine("• ${n.title.ifBlank { "Untitled" }}: ${Redaction.forAi(n.body).take(800)}")
            }
            appendLine()
        }
        if (session.roadmapAttached.value) {
            savedRoadmap.value?.let { rm ->
                appendLine("The learner's roadmap for today:")
                appendLine(formatRoadmap(rm.steps))
                appendLine()
            }
        }
        // Recent conversation (excluding system lines) so the tutor keeps continuity. The live
        // streaming bubble (blank) is naturally excluded because it's appended after this builds.
        val history = session.messages.value.filter { it.role != TutorRole.SYSTEM }.takeLast(8)
        if (history.isNotEmpty()) {
            appendLine("Conversation so far:")
            history.forEach { m ->
                val who = if (m.role == TutorRole.USER) "Learner" else "Tutor"
                appendLine("$who: ${m.text}")
            }
            appendLine()
        }
        append("Learner: $input")
    }

    companion object {
        /** Questions per quiz round (short enough to finish, long enough to mean something). */
        private const val QUIZ_LENGTH = 6

        private const val TUTOR_PERSONA =
            "You are a patient, encouraging study tutor. Teach using the learner's own attached notes " +
                "and roadmap as the ground truth where relevant; if something isn't covered, say so " +
                "briefly and still help. Prefer a Socratic style: ask one question at a time when " +
                "quizzing, give feedback on answers, and keep replies concise and conversational."

        fun formatRoadmap(steps: List<RoadmapStep>): String = steps.joinToString("\n") { s ->
            val time = if (s.time.isBlank()) "" else "${s.time} — "
            val detail = if (s.detail.isBlank()) "" else " (${s.detail})"
            "$time${s.title}$detail"
        }
    }
}
