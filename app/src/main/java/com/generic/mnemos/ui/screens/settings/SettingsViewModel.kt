package com.generic.mnemos.ui.screens.settings

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.generic.mnemos.core.ai.llm.LlmClient
import com.generic.mnemos.core.ai.llm.OpenAiCompatPreset
import com.generic.mnemos.core.ai.llm.ProviderId
import com.generic.mnemos.core.auth.SecureKeyStore
import com.generic.mnemos.core.settings.AmbientPrefs
import com.generic.mnemos.core.settings.DeliveryMode
import com.generic.mnemos.core.settings.SettingsRepository
import com.generic.mnemos.service.ambient.AmbientService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface KeyStatus {
    data object Idle : KeyStatus
    data object Validating : KeyStatus
    data object Valid : KeyStatus
    data class Invalid(val message: String) : KeyStatus
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyStore: SecureKeyStore,
    private val llm: LlmClient,
    private val settings: SettingsRepository,
) : ViewModel() {

    val prefs: StateFlow<AmbientPrefs> =
        settings.prefs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AmbientPrefs())

    /** Accessibility note font size (sp), shared with the note editor. */
    val noteFontSp: StateFlow<Float> =
        settings.noteFontSp.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 16f)
    fun setNoteFontSp(sp: Float) { viewModelScope.launch { settings.setNoteFontSp(sp) } }

    /** The editable command note (slash-command reference for the terminal palette + /help). */
    val commandNote: StateFlow<String> =
        settings.commandNote.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            com.generic.mnemos.core.commands.CommandNote.DEFAULT,
        )
    fun setCommandNote(text: String) { viewModelScope.launch { settings.setCommandNote(text) } }
    fun resetCommandNote() {
        viewModelScope.launch { settings.setCommandNote(com.generic.mnemos.core.commands.CommandNote.DEFAULT) }
    }

    private val _hasKey = MutableStateFlow(keyStore.hasAnyKey())
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    /** Which provider calls currently route to (null until a key is saved / Nano chosen). */
    private val _activeProvider = MutableStateFlow(llm.activeProvider)
    val activeProvider: StateFlow<ProviderId?> = _activeProvider.asStateFlow()

    private val _keyStatus = MutableStateFlow<KeyStatus>(KeyStatus.Idle)
    val keyStatus: StateFlow<KeyStatus> = _keyStatus.asStateFlow()

    /** Paste any provider key — we infer Gemini/OpenAI/Claude from its prefix and route to it. */
    fun saveAndValidateKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return
        val provider = ProviderId.fromKey(trimmed)
        if (provider == null) {
            _keyStatus.value = KeyStatus.Invalid(
                "Unrecognised key. Expected Gemini (AIza…), OpenAI (sk-…) or Claude (sk-ant-…).",
            )
            return
        }
        // An OpenRouter (sk-or-) / Groq (gsk_) key auto-configures its endpoint + default model.
        if (provider == ProviderId.OPENAI_COMPAT) {
            OpenAiCompatPreset.fromKey(trimmed)?.let { preset ->
                keyStore.compatBaseUrl = preset.baseUrl
                keyStore.compatModel = preset.defaultModel
                _compatBaseUrl.value = preset.baseUrl
                _compatModel.value = preset.defaultModel
            }
        }
        keyStore.setKey(provider, trimmed)
        keyStore.selectedProvider = provider
        _hasKey.value = keyStore.hasAnyKey()
        _activeProvider.value = llm.activeProvider
        _keyStatus.value = KeyStatus.Validating
        viewModelScope.launch {
            _keyStatus.value = llm.validateKey().fold(
                onSuccess = { KeyStatus.Valid },
                onFailure = { KeyStatus.Invalid(it.message ?: "Key rejected.") },
            )
        }
    }

    // --- OpenAI-compatible presets (Groq / OpenRouter / DeepSeek / custom) ---

    private val _compatBaseUrl = MutableStateFlow(keyStore.compatBaseUrl ?: "")
    val compatBaseUrl: StateFlow<String> = _compatBaseUrl.asStateFlow()

    private val _compatModel = MutableStateFlow(keyStore.compatModel ?: "")
    val compatModel: StateFlow<String> = _compatModel.asStateFlow()

    fun selectCompatPreset(preset: OpenAiCompatPreset) {
        if (preset != OpenAiCompatPreset.CUSTOM) {
            _compatBaseUrl.value = preset.baseUrl
            _compatModel.value = preset.defaultModel
        }
    }

    fun setCompatBaseUrl(v: String) { _compatBaseUrl.value = v }
    fun setCompatModel(v: String) { _compatModel.value = v }

    /** Save a key for the chosen OpenAI-compatible endpoint (covers DeepSeek + custom). */
    fun saveCompatKey(key: String) {
        val k = key.trim()
        if (k.isBlank()) return
        val url = _compatBaseUrl.value.trim()
        val model = _compatModel.value.trim()
        if (url.isBlank() || model.isBlank()) {
            _keyStatus.value = KeyStatus.Invalid("Pick a preset (or set a base URL + model) first.")
            return
        }
        keyStore.compatBaseUrl = url
        keyStore.compatModel = model
        keyStore.setKey(ProviderId.OPENAI_COMPAT, k)
        keyStore.selectedProvider = ProviderId.OPENAI_COMPAT
        _hasKey.value = keyStore.hasAnyKey()
        _activeProvider.value = llm.activeProvider
        _keyStatus.value = KeyStatus.Validating
        viewModelScope.launch {
            _keyStatus.value = llm.validateKey().fold(
                onSuccess = { KeyStatus.Valid },
                onFailure = { KeyStatus.Invalid(it.message ?: "Key rejected.") },
            )
        }
    }

    /** Switch to the keyless on-device provider (Gemini Nano). */
    fun useOnDeviceAi() {
        keyStore.selectedProvider = ProviderId.NANO
        _activeProvider.value = llm.activeProvider
        _keyStatus.value = KeyStatus.Idle
    }

    fun clearKey() {
        ProviderId.entries.filter { it.needsKey }.forEach { keyStore.setKey(it, null) }
        keyStore.selectedProvider = null
        _hasKey.value = false
        _activeProvider.value = llm.activeProvider
        _keyStatus.value = KeyStatus.Idle
    }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }
    fun setIntervalMinutes(min: Int) = update { it.copy(intervalMinutes = min) }
    fun setDelivery(mode: DeliveryMode) = update { it.copy(delivery = mode) }
    fun setQuietHours(start: Int, end: Int) = update { it.copy(quietStartHour = start, quietEndHour = end) }
    fun setSpeed(speed: Float) = update { it.copy(ttsSpeed = speed) }

    fun testSpeak() =
        AmbientService.speak(context, "This is Mnemos, reading your notes aloud. Tap skip any time.")

    fun surfaceNow() = AmbientService.surface(context)

    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(context)

    private fun update(transform: (AmbientPrefs) -> AmbientPrefs) {
        viewModelScope.launch { settings.update(transform) }
    }
}
