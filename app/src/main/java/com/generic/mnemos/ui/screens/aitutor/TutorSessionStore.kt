package com.generic.mnemos.ui.screens.aitutor

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The tutor conversation, held app-wide so leaving the tab and coming back resumes the session
 * (the ViewModel dies with the nav entry; this @Singleton doesn't). In-memory only — a session
 * concept, deliberately not persisted across process death (mirrors ActiveNoteStore).
 */
@Singleton
class TutorSessionStore @Inject constructor() {
    private val _messages = MutableStateFlow<List<TutorMessage>>(emptyList())
    val messages: StateFlow<List<TutorMessage>> = _messages.asStateFlow()

    private val _attachedNoteIds = MutableStateFlow<Set<Long>>(emptySet())
    val attachedNoteIds: StateFlow<Set<Long>> = _attachedNoteIds.asStateFlow()

    private val _roadmapAttached = MutableStateFlow(false)
    val roadmapAttached: StateFlow<Boolean> = _roadmapAttached.asStateFlow()

    fun append(message: TutorMessage) { _messages.value = _messages.value + message }

    /** Replace the LAST message's text — the streaming sink (the last bubble grows live). */
    fun updateLast(text: String) {
        val list = _messages.value
        if (list.isEmpty()) return
        _messages.value = list.dropLast(1) + list.last().copy(text = text)
    }

    /** Drop a just-appended empty streaming bubble after a failure, so no blank bubble lingers. */
    fun dropLastIfBlank() {
        val list = _messages.value
        if (list.isNotEmpty() && list.last().text.isBlank()) _messages.value = list.dropLast(1)
    }

    fun setMessages(messages: List<TutorMessage>) { _messages.value = messages }

    fun toggleNote(id: Long) {
        _attachedNoteIds.value = _attachedNoteIds.value.let { if (id in it) it - id else it + id }
    }

    fun setRoadmapAttached(attached: Boolean) { _roadmapAttached.value = attached }

    fun clear() {
        _messages.value = emptyList()
        _attachedNoteIds.value = emptySet()
        _roadmapAttached.value = false
    }
}
