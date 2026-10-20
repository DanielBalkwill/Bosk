package com.generic.mnemos.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.generic.mnemos.core.notes.NotesRepository
import com.generic.mnemos.core.review.ReviewRepository
import com.generic.mnemos.core.review.Streak
import com.generic.mnemos.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Everything the stats screen shows, derived reactively from the review log + FSRS aggregates. */
data class StatsUi(
    val streak: Int = 0,
    val today: Int = 0,
    val due: Int = 0,
    val totalCards: Int = 0,
    /** Average memory-strength score across decks, as a percentage. */
    val overallPct: Int = 0,
    /** Reviews per day, oldest→today (30 entries). */
    val last30: List<Int> = emptyList(),
    /** The weakest decks (note title → 0..1 score), weakest first. */
    val weakest: List<Pair<String, Float>> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    settings: SettingsRepository,
    reviews: ReviewRepository,
    notes: NotesRepository,
) : ViewModel() {

    val ui: StateFlow<StatsUi> =
        combine(
            settings.reviewLog,
            reviews.observeDueCount(),
            reviews.observeNoteHealth(),
            notes.observeNotes(),
        ) { log, due, health, allNotes ->
            val today = LocalDate.now().toEpochDay()
            val activeDays = log.filterValues { it > 0 }.keys
            val titles = allNotes.associate { it.id to it.title }
            StatsUi(
                streak = Streak.current(activeDays, today),
                today = log[today] ?: 0,
                due = due,
                totalCards = health.values.sumOf { it.total },
                overallPct = if (health.isEmpty()) 0
                else (health.values.map { it.score.toDouble() }.average() * 100).toInt(),
                last30 = (29 downTo 0).map { log[today - it] ?: 0 },
                weakest = health.entries.sortedBy { it.value.score }.take(6)
                    .map { (id, h) -> (titles[id]?.ifBlank { "Untitled" } ?: "Note $id") to h.score },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUi())
}
