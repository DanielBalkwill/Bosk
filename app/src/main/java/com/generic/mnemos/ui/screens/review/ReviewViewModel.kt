package com.generic.mnemos.ui.screens.review

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.generic.mnemos.core.ai.KnowledgeProfileUpdater
import com.generic.mnemos.core.notes.Note
import com.generic.mnemos.core.notes.NotesRepository
import com.generic.mnemos.core.review.AnkiImporter
import com.generic.mnemos.core.review.Deck
import com.generic.mnemos.core.review.ReviewCard
import com.generic.mnemos.core.review.ReviewRepository
import com.generic.mnemos.core.srs.Rating
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewState(
    val loading: Boolean = true,
    val cards: List<ReviewCard> = emptyList(),
    val index: Int = 0,
    val showBack: Boolean = false,
    val reviewedCount: Int = 0,
    val correctCount: Int = 0,
    val finished: Boolean = false,
) {
    val current: ReviewCard? get() = cards.getOrNull(index)
    val retentionPct: Int get() = if (reviewedCount == 0) 0 else (correctCount * 100) / reviewedCount
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val reviews: ReviewRepository,
    private val notes: NotesRepository,
    private val profileUpdater: KnowledgeProfileUpdater,
    private val ankiImporter: AnkiImporter,
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewState())
    val state: StateFlow<ReviewState> = _state.asStateFlow()

    /** One-shot user-facing message (import results / errors), shown in a snackbar. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    /** Decks (notes with cards) shown in the Practise picker. */
    private val _decks = MutableStateFlow<List<Deck>>(emptyList())
    val decks: StateFlow<List<Deck>> = _decks.asStateFlow()

    private val outcomes = mutableListOf<KnowledgeProfileUpdater.Outcome>()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = ReviewState(loading = true)
            outcomes.clear()
            val due = reviews.dueCards()
            _state.value = ReviewState(loading = false, cards = due, finished = due.isEmpty())
            refreshDecks()
        }
    }

    private suspend fun refreshDecks() {
        _decks.value = runCatching { reviews.decks() }.getOrDefault(emptyList())
    }

    /** Leave the current card session and return to the deck list / summary. */
    fun exitToDecks() {
        _state.update { it.copy(finished = true) }
        viewModelScope.launch { refreshDecks() }
    }

    /** Study a specific deck on demand — all of its cards, regardless of due date. */
    fun studyDeck(noteId: Long) {
        viewModelScope.launch {
            _state.value = ReviewState(loading = true)
            outcomes.clear()
            val cards = reviews.cardsForNote(noteId)
            _state.value = ReviewState(loading = false, cards = cards, finished = cards.isEmpty())
        }
    }

    fun flip() = _state.update { it.copy(showBack = true) }

    fun rate(rating: Rating) {
        val card = _state.value.current ?: return
        viewModelScope.launch {
            try {
                val noteId = reviews.recordReview(card.id, rating)
                outcomes += KnowledgeProfileUpdater.Outcome(noteId, rating)

                _state.update { s ->
                    val correct = s.correctCount + if (rating == Rating.GOOD || rating == Rating.EASY) 1 else 0
                    val nextIndex = s.index + 1
                    val finished = nextIndex >= s.cards.size
                    s.copy(
                        index = nextIndex,
                        showBack = false,
                        reviewedCount = s.reviewedCount + 1,
                        correctCount = correct,
                        finished = finished,
                    )
                }
                if (_state.value.finished) profileUpdater.onSessionComplete(outcomes.toList())
            } catch (e: Exception) {
                Log.e("Review", "rate failed", e)
            }
        }
    }

    /** Manual card creation — attaches to a reusable "Quick Cards" holder note. */
    fun addManualCard(front: String, back: String) {
        if (front.isBlank() || back.isBlank()) return
        viewModelScope.launch {
            val holderId = ensureQuickCardsNote()
            reviews.addCard(holderId, front, back, aiGenerated = false)
            load()
        }
    }

    private suspend fun ensureQuickCardsNote(): Long {
        notes.search(QUICK_CARDS_TITLE).firstOrNull { it.title == QUICK_CARDS_TITLE }?.let { return it.id }
        return notes.upsert(Note(title = QUICK_CARDS_TITLE, body = "Manually created flashcards."))
    }

    /** Import an Anki export (.apkg or plain text) — one holder note (deck) per Anki deck. */
    fun importAnki(uri: Uri) {
        viewModelScope.launch {
            _message.value = "Importing…"
            ankiImporter.import(uri).fold(
                onSuccess = { decks ->
                    val total = decks.sumOf { it.cards.size }
                    if (total == 0) {
                        _message.value = "No cards found in that file."
                        return@fold
                    }
                    decks.forEach { deck ->
                        val holderId = ensureDeckNote(deck.name)
                        deck.cards.forEach { reviews.addCard(holderId, it.front, it.back, aiGenerated = false) }
                    }
                    _message.value = "Imported $total card${if (total == 1) "" else "s"}" +
                        if (decks.size > 1) " across ${decks.size} decks." else "."
                    load()
                },
                onFailure = { e ->
                    Log.e("Review", "anki import failed", e)
                    _message.value = e.message ?: "Import failed."
                },
            )
        }
    }

    /** Find-or-create the holder note that represents an imported deck of [name]. */
    private suspend fun ensureDeckNote(name: String): Long {
        val title = name.ifBlank { IMPORTED_TITLE }
        notes.search(title).firstOrNull { it.title == title }?.let { return it.id }
        return notes.upsert(Note(title = title, body = "Flashcards imported from Anki."))
    }

    private companion object {
        const val QUICK_CARDS_TITLE = "Quick Cards"
        const val IMPORTED_TITLE = "Imported (Anki)"
    }
}
