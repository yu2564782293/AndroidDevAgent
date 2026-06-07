package com.example.androiddevagent.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.androiddevagent.utils.InputValidator
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val keyStoreHelper: KeyStoreHelper
) {

    val configFlow: Flow<LLMConfig> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences.toConfig() }
        .distinctUntilChanged()

    suspend fun getConfig(): LLMConfig {
        return configFlow.first()
    }

    suspend fun saveConfig(config: LLMConfig) {
        val sanitizedConfig = config.sanitized()

        dataStore.edit { preferences ->
            preferences[Keys.PROVIDER_NAME] = sanitizedConfig.provider.name
            preferences[Keys.API_KEY] = if (sanitizedConfig.apiKey.isBlank()) {
                ""
            } else {
                keyStoreHelper.encrypt(sanitizedConfig.apiKey.trim())
            }
            preferences[Keys.BASE_URL] = sanitizedConfig.baseUrl
            preferences[Keys.MODEL_NAME] = sanitizedConfig.modelName
            preferences[Keys.TEMPERATURE] = sanitizedConfig.temperature
            preferences[Keys.TOP_P] = sanitizedConfig.topP
            preferences[Keys.MAX_TOKENS] = sanitizedConfig.maxTokens
        }
    }

    suspend fun updateConfig(transform: (LLMConfig) -> LLMConfig) {
        saveConfig(transform(getConfig()))
    }

    suspend fun clearApiKey() {
        updateConfig { config -> config.copy(apiKey = "") }
    }

    private fun Preferences.toConfig(): LLMConfig {
        val providerName = this[Keys.PROVIDER_NAME] ?: LLMConfig.DEFAULT.providerName
        val provider = LLMProvider.fromProviderName(providerName)
        val defaultConfig = LLMConfig.DEFAULT.withProviderDefaults(provider)
        val encryptedApiKey = this[Keys.API_KEY].orEmpty()

        return LLMConfig(
            providerName = provider.name,
            apiKey = encryptedApiKey.decryptOrEmpty(),
            baseUrl = this[Keys.BASE_URL] ?: defaultConfig.baseUrl,
            modelName = this[Keys.MODEL_NAME] ?: defaultConfig.modelName,
            temperature = this[Keys.TEMPERATURE] ?: defaultConfig.temperature,
            topP = this[Keys.TOP_P] ?: defaultConfig.topP,
            maxTokens = this[Keys.MAX_TOKENS] ?: defaultConfig.maxTokens
        ).sanitized()
    }

    private fun String.decryptOrEmpty(): String {
        if (isBlank()) return ""
        return runCatching { keyStoreHelper.decrypt(this) }.getOrDefault("")
    }

    private fun LLMConfig.sanitized(): LLMConfig {
        return copy(
            providerName = provider.name,
            apiKey = InputValidator.sanitizeApiKey(apiKey),
            baseUrl = InputValidator.sanitizeBaseUrl(baseUrl),
            modelName = InputValidator.sanitizeModelName(modelName),
            temperature = temperature.coerceIn(0.0, 2.0),
            topP = topP.coerceIn(0.0, 1.0),
            maxTokens = maxTokens.coerceAtLeast(1)
        )
    }

    private object Keys {
        val PROVIDER_NAME = stringPreferencesKey("llm_provider_name")
        val API_KEY = stringPreferencesKey("llm_api_key_encrypted")
        val BASE_URL = stringPreferencesKey("llm_base_url")
        val MODEL_NAME = stringPreferencesKey("llm_model_name")
        val TEMPERATURE = doublePreferencesKey("llm_temperature")
        val TOP_P = doublePreferencesKey("llm_top_p")
        val MAX_TOKENS = intPreferencesKey("llm_max_tokens")
    }
}
