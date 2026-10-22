package app.bosk.core.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.bosk.core.ai.llm.ProviderId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single home for the user's AI API keys (BYOK) and any other secrets. Bosk is
 * multi-provider: a key can be stored per [ProviderId] (Gemini / OpenAI / Claude), plus a
 * remembered [selectedProvider] so the app knows which to route to (incl. keyless Nano).
 *
 * Global rule #5 — the API key is sacred: it lives only in [EncryptedSharedPreferences]
 * (AES-256, key material in the Android Keystore), is excluded from cloud backup and
 * device transfer (see the res/xml backup + data-extraction rules), is never logged, and is
 * only ever sent to its own provider's endpoint.
 *
 * The prefs file name [PREFS_FILE] MUST match the backup-exclusion paths in the manifest rules.
 */
@Singleton
class SecureKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** The stored key for [provider], or null if none/blank. */
    fun key(provider: ProviderId): String? =
        prefs.getString(prefKey(provider), null)?.takeIf { it.isNotBlank() }

    /** Store (or, with null/blank, remove) the key for [provider]. */
    fun setKey(provider: ProviderId, value: String?) = prefs.edit().apply {
        val k = prefKey(provider)
        if (value.isNullOrBlank()) remove(k) else putString(k, value.trim())
    }.apply()

    fun hasKey(provider: ProviderId): Boolean = key(provider) != null

    /** True if at least one *keyed* provider has a key (Nano has no key). */
    fun hasAnyKey(): Boolean = ProviderId.entries.any { it.needsKey && hasKey(it) }

    /** True if the app can run AI at all: a key exists, or Nano is the chosen provider. */
    fun isReady(): Boolean = hasAnyKey() || selectedProvider == ProviderId.NANO

    /** Base URL for the OpenAI-compatible provider (Groq / OpenRouter / DeepSeek / custom). */
    var compatBaseUrl: String?
        get() = prefs.getString(KEY_COMPAT_URL, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_COMPAT_URL) else putString(KEY_COMPAT_URL, value.trim())
        }.apply()

    /** Model id for the OpenAI-compatible provider. */
    var compatModel: String?
        get() = prefs.getString(KEY_COMPAT_MODEL, null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().apply {
            if (value.isNullOrBlank()) remove(KEY_COMPAT_MODEL) else putString(KEY_COMPAT_MODEL, value.trim())
        }.apply()

    /** The provider the user last chose, persisted so routing is stable across launches. */
    var selectedProvider: ProviderId?
        get() = prefs.getString(KEY_SELECTED, null)
            ?.let { runCatching { ProviderId.valueOf(it) }.getOrNull() }
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_SELECTED) else putString(KEY_SELECTED, value.name)
        }.apply()

    fun clear() = prefs.edit().clear().apply()

    private fun prefKey(provider: ProviderId): String = when (provider) {
        // Preserve the legacy name so existing Gemini keys survive the multi-provider upgrade.
        ProviderId.GEMINI -> "gemini_api_key"
        ProviderId.OPENAI -> "openai_api_key"
        ProviderId.ANTHROPIC -> "anthropic_api_key"
        ProviderId.OPENAI_COMPAT -> "openai_compat_api_key"
        ProviderId.NANO -> "nano_unused" // keyless; never written
    }

    companion object {
        // Must match the <exclude> paths in backup_rules.xml / data_extraction_rules.xml.
        const val PREFS_FILE = "bosk_secure_prefs"
        private const val KEY_SELECTED = "selected_provider"
        private const val KEY_COMPAT_URL = "compat_base_url"
        private const val KEY_COMPAT_MODEL = "compat_model"
    }
}
