package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.keyPoolDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "agent_desk_key_pool"
)

enum class KeySource {
    IN_APP,
    SECRETS_FALLBACK,
    UNCONFIGURED
}

data class StoredKeySlot(
    val slotIndex: Int, // 1..4
    val label: String,
    val customKey: String,
    val secretFallbackKey: String,
    val enabled: Boolean
) {
    val effectiveKey: String
        get() = when {
            customKey.isNotBlank() -> customKey.trim()
            isValidSecret(secretFallbackKey) -> secretFallbackKey.trim()
            else -> ""
        }

    val source: KeySource
        get() = when {
            customKey.isNotBlank() -> KeySource.IN_APP
            isValidSecret(secretFallbackKey) -> KeySource.SECRETS_FALLBACK
            else -> KeySource.UNCONFIGURED
        }

    val isConfigured: Boolean
        get() = effectiveKey.isNotBlank()

    companion object {
        private fun isValidSecret(value: String): Boolean {
            val trimmed = value.trim()
            return trimmed.isNotEmpty() &&
                !trimmed.equals("UNSET", ignoreCase = true) &&
                !trimmed.startsWith("MY_GEMINI_API_KEY", ignoreCase = true)
        }
    }
}

data class AgencySettings(
    val activeModelId: String = DEFAULT_MODEL_ID,
    val keySlots: List<StoredKeySlot> = emptyList(),
    val thinkingEnabled: Boolean = true
) {
    companion object {
        const val DEFAULT_MODEL_ID = "gemini-3.1-flash-lite-preview"
        val AVAILABLE_MODELS: List<Pair<String, String>> = listOf(
            "gemini-3.1-flash-lite-preview" to "Gemini 3.1 Flash-Lite (Preview)",
            "gemini-3.1-flash-lite" to "Gemini 3.1 Flash-Lite (GA)",
            "gemini-2.5-flash-lite" to "Gemini 2.5 Flash-Lite",
            "gemini-2.5-flash" to "Gemini 2.5 Flash"
        )
    }
}

class KeyPoolPreferences(private val context: Context) {

    private val modelKey = stringPreferencesKey("active_model_id")
    private val thinkingEnabledKey = booleanPreferencesKey("thinking_enabled")

    private fun slotKeyPref(index: Int) = stringPreferencesKey("api_key_slot_$index")
    private fun slotLabelPref(index: Int) = stringPreferencesKey("api_key_label_$index")
    private fun slotEnabledPref(index: Int) = booleanPreferencesKey("api_key_enabled_$index")

    private fun secretFallbackFor(index: Int): String = when (index) {
        1 -> BuildConfig.GEMINI_API_KEY_1
        2 -> BuildConfig.GEMINI_API_KEY_2
        3 -> BuildConfig.GEMINI_API_KEY_3
        4 -> BuildConfig.GEMINI_API_KEY_4
        else -> ""
    }

    val settingsFlow: Flow<AgencySettings> = context.keyPoolDataStore.data.map { prefs ->
        val slots = (1..4).map { idx ->
            StoredKeySlot(
                slotIndex = idx,
                label = prefs[slotLabelPref(idx)] ?: "GCP Project Key #$idx",
                customKey = prefs[slotKeyPref(idx)] ?: "",
                secretFallbackKey = secretFallbackFor(idx),
                enabled = prefs[slotEnabledPref(idx)] ?: true
            )
        }
        AgencySettings(
            activeModelId = prefs[modelKey] ?: AgencySettings.DEFAULT_MODEL_ID,
            keySlots = slots,
            thinkingEnabled = prefs[thinkingEnabledKey] ?: true
        )
    }

    suspend fun saveKeySlot(slotIndex: Int, label: String, apiKey: String, enabled: Boolean) {
        context.keyPoolDataStore.edit { prefs ->
            prefs[slotLabelPref(slotIndex)] = label.ifBlank { "GCP Project Key #$slotIndex" }
            prefs[slotKeyPref(slotIndex)] = apiKey.trim()
            prefs[slotEnabledPref(slotIndex)] = enabled
        }
    }

    suspend fun setSlotEnabled(slotIndex: Int, enabled: Boolean) {
        context.keyPoolDataStore.edit { prefs ->
            prefs[slotEnabledPref(slotIndex)] = enabled
        }
    }

    suspend fun setActiveModel(modelId: String) {
        context.keyPoolDataStore.edit { prefs ->
            prefs[modelKey] = modelId
        }
    }

    suspend fun setThinkingEnabled(enabled: Boolean) {
        context.keyPoolDataStore.edit { prefs ->
            prefs[thinkingEnabledKey] = enabled
        }
    }
}
