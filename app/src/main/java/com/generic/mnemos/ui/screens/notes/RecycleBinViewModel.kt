package com.generic.mnemos.ui.screens.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.generic.mnemos.core.notes.Note
import com.generic.mnemos.core.notes.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the Recycle Bin screen: list trashed notes and restore / permanently delete them. */
@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val repo: NotesRepository,
) : ViewModel() {

    val trashed: StateFlow<List<Note>> =
        repo.observeTrashed().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    fun restore(note: Note) = viewModelScope.launch {
        repo.restore(note.id)
        _message.value = "Restored \"${note.title.ifBlank { "Untitled" }}\""
    }

    fun deleteForever(note: Note) = viewModelScope.launch {
        repo.deleteForever(note.id)
        _message.value = "Deleted \"${note.title.ifBlank { "Untitled" }}\" forever"
    }

    fun emptyBin() = viewModelScope.launch {
        repo.emptyTrash()
        _message.value = "Recycle bin emptied"
    }
}
