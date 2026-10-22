package app.bosk.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bosk.core.review.ReviewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Feeds the due-count badge on the Review tab. */
@HiltViewModel
class NavBadgeViewModel @Inject constructor(
    reviews: ReviewRepository,
) : ViewModel() {
    val dueCount: StateFlow<Int> =
        reviews.observeDueCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
