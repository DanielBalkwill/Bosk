package com.generic.mnemos.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.generic.mnemos.core.ai.llm.LlmClient
import com.generic.mnemos.core.ai.llm.OpenAiCompatPreset
import com.generic.mnemos.core.ai.llm.ProviderId
import com.generic.mnemos.core.auth.SecureKeyStore
import com.generic.mnemos.core.notes.Note
import com.generic.mnemos.core.notes.NotesRepository
import com.generic.mnemos.core.settings.SettingsRepository
import com.generic.mnemos.ui.screens.settings.KeyStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val keyStore: SecureKeyStore,
    private val llm: LlmClient,
    private val settings: SettingsRepository,
    private val notes: NotesRepository,
) : ViewModel() {

    private val _hasKey = MutableStateFlow(keyStore.isReady())
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    private val _keyStatus = MutableStateFlow<KeyStatus>(KeyStatus.Idle)
    val keyStatus: StateFlow<KeyStatus> = _keyStatus.asStateFlow()

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
        if (provider == ProviderId.OPENAI_COMPAT) {
            OpenAiCompatPreset.fromKey(trimmed)?.let { preset ->
                keyStore.compatBaseUrl = preset.baseUrl
                keyStore.compatModel = preset.defaultModel
            }
        }
        keyStore.setKey(provider, trimmed)
        keyStore.selectedProvider = provider
        _hasKey.value = keyStore.isReady()
        _keyStatus.value = KeyStatus.Validating
        viewModelScope.launch {
            _keyStatus.value = llm.validateKey().fold(
                onSuccess = { KeyStatus.Valid },
                onFailure = { KeyStatus.Invalid(it.message ?: "Key rejected.") },
            )
        }
    }

    /** Skip keys entirely and use the on-device free tier. */
    fun useOnDeviceAi() {
        keyStore.selectedProvider = ProviderId.NANO
        _hasKey.value = keyStore.isReady()
        _keyStatus.value = KeyStatus.Idle
    }

    fun clearKey() {
        ProviderId.entries.filter { it.needsKey }.forEach { keyStore.setKey(it, null) }
        keyStore.selectedProvider = null
        _hasKey.value = false
        _keyStatus.value = KeyStatus.Idle
    }

    fun addFirstNote(title: String, body: String) {
        if (title.isBlank() && body.isBlank()) return
        viewModelScope.launch { notes.upsert(Note(title = title, body = body)) }
    }

    fun finish() {
        viewModelScope.launch { settings.setOnboardingComplete(true) }
    }
}
