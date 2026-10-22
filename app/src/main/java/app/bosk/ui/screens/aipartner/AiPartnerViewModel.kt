package app.bosk.ui.screens.aipartner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bosk.core.ai.Persona
import app.bosk.core.ai.PersonaStyle
import app.bosk.core.ai.Verbosity
import app.bosk.core.ai.llm.LlmClient
import app.bosk.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiPartnerViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val llm: LlmClient,
) : ViewModel() {

    /** The live, persisted persona. Edits write straight through to settings. */
    val persona: StateFlow<Persona> =
        settings.persona.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Persona())

    // ── A small "see it in action" preview, calling the AI with the persona already applied ──────
    private val _preview = MutableStateFlow("")
    val preview: StateFlow<String> = _preview.asStateFlow()
    private val _previewBusy = MutableStateFlow(false)
    val previewBusy: StateFlow<Boolean> = _previewBusy.asStateFlow()

    private fun edit(transform: (Persona) -> Persona) {
        viewModelScope.launch { settings.savePersona(transform(persona.value)) }
    }

    fun setName(name: String) = edit { it.copy(name = name) }
    fun setStyle(style: PersonaStyle) = edit { it.copy(style = style) }
    fun setVerbosity(v: Verbosity) = edit { it.copy(verbosity = v) }
    fun setCustomInstruction(text: String) = edit { it.copy(customInstruction = text) }
    fun setUseEmoji(use: Boolean) = edit { it.copy(useEmoji = use) }
    fun reset() = edit { Persona() }

    /** Ask the AI to introduce itself — the persona is injected app-wide, so this reflects the config. */
    fun previewReply() {
        if (_previewBusy.value) return
        _previewBusy.value = true
        _preview.value = ""
        viewModelScope.launch {
            val prompt = "Introduce yourself in two or three sentences and suggest one thing we could work " +
                "on next. Stay fully in character."
            llm.generate(prompt).fold(
                onSuccess = { _preview.value = it.trim() },
                onFailure = { _preview.value = "! ${it.message ?: "request failed"}" },
            )
            _previewBusy.value = false
        }
    }
}
