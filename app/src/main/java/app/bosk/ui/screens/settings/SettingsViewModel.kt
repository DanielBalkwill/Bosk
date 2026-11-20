package app.bosk.ui.screens.settings

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bosk.core.ai.llm.LlmClient
import app.bosk.core.ai.llm.OpenAiCompatPreset
import app.bosk.core.ai.llm.ProviderId
import app.bosk.core.auth.SecureKeyStore
import app.bosk.core.settings.AmbientPrefs
import app.bosk.core.settings.DeliveryMode
import app.bosk.core.settings.SettingsRepository
import app.bosk.core.settings.ThemeMode
import app.bosk.service.ambient.AmbientService
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

    /** Appearance: theme mode (system/light/dark) + wallpaper dynamic colour. */
    val themeMode: StateFlow<ThemeMode> =
        settings.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { settings.setThemeMode(mode) } }

    val dynamicColor: StateFlow<Boolean> =
        settings.dynamicColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setDynamicColor(on: Boolean) { viewModelScope.launch { settings.setDynamicColor(on) } }

    /** The editable command note (slash-command reference for the terminal palette + /help). */
    val commandNote: StateFlow<String> =
        settings.commandNote.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            app.bosk.core.commands.CommandNote.DEFAULT,
        )
    fun setCommandNote(text: String) { viewModelScope.launch { settings.setCommandNote(text) } }
    fun resetCommandNote() {
        viewModelScope.launch { settings.setCommandNote(app.bosk.core.commands.CommandNote.DEFAULT) }
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

    // --- Multi-provider failover chain (ordered priority; primary first, rest are backups) ---

    private val _providerChain = MutableStateFlow(computeChain())
    val providerChain: StateFlow<List<ProviderId>> = _providerChain.asStateFlow()

    /** Configured providers (those with a key, plus Nano if the user chose it), in failover order. */
    private fun computeChain(): List<ProviderId> {
        val order = keyStore.providerOrder
        val keyed = ProviderId.entries.filter { it.needsKey && keyStore.hasKey(it) }
        val nano = if (ProviderId.NANO in order) listOf(ProviderId.NANO) else emptyList()
        val configured = keyed + nano
        return (order.filter { it in configured } + configured.filter { it !in order }).distinct()
    }

    /** Recompute from the key store — called when keys are added/removed/validated. */
    fun refreshProviderChain() { _providerChain.value = computeChain() }

    /** Move a provider up/down the failover chain (top = primary, the rest ordered backups). */
    fun moveProvider(provider: ProviderId, up: Boolean) {
        val chain = _providerChain.value.toMutableList()
        val i = chain.indexOf(provider)
        if (i < 0) return
        val j = if (up) i - 1 else i + 1
        if (j !in chain.indices) return
        val tmp = chain[i]; chain[i] = chain[j]; chain[j] = tmp
        keyStore.providerOrder = chain
        _providerChain.value = chain
        _activeProvider.value = chain.firstOrNull()
    }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }
    fun setIntervalMinutes(min: Int) = update { it.copy(intervalMinutes = min) }
    fun setDelivery(mode: DeliveryMode) = update { it.copy(delivery = mode) }
    fun setQuietHours(start: Int, end: Int) = update { it.copy(quietStartHour = start, quietEndHour = end) }
    fun setSpeed(speed: Float) = update { it.copy(ttsSpeed = speed) }

    fun testSpeak() =
        AmbientService.speak(context, "This is Bosk, reading your notes aloud. Tap skip any time.")

    fun surfaceNow() = AmbientService.surface(context)

    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(context)

    private fun update(transform: (AmbientPrefs) -> AmbientPrefs) {
        viewModelScope.launch { settings.update(transform) }
    }
}
