package com.generic.mnemos.ui.screens.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.generic.mnemos.core.queue.QueueItem
import com.generic.mnemos.core.queue.QueueKind
import com.generic.mnemos.core.queue.QueueRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val repo: QueueRepository,
) : ViewModel() {

    val items: StateFlow<List<QueueItem>> =
        repo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(title: String, kind: QueueKind, timeMs: Long?) {
        if (title.isBlank()) return
        viewModelScope.launch { repo.add(title, kind, timeMs) }
    }

    fun remove(id: Long) = viewModelScope.launch { repo.remove(id) }
    fun toggleDone(id: Long) = viewModelScope.launch { repo.toggleDone(id) }
    fun moveUp(id: Long) = viewModelScope.launch { repo.move(id, up = true) }
    fun moveDown(id: Long) = viewModelScope.launch { repo.move(id, up = false) }
}
