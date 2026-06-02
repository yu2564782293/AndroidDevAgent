package com.example.androiddevagent.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            "secure_agent_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveApiKey(provider: String, apiKey: String) {
        encryptedPrefs.edit().putString("api_key_$provider", apiKey).apply()
    }

    fun getApiKey(provider: String): String {
        return encryptedPrefs.getString("api_key_$provider", "") ?: ""
    }

    fun saveActiveProvider(provider: String) {
        encryptedPrefs.edit().putString("active_provider", provider).apply()
    }

    fun getActiveProvider(): String {
        return encryptedPrefs.getString("active_provider", "openai") ?: "openai"
    }

    fun saveProviderConfig(provider: String, baseUrl: String, modelName: String) {
        encryptedPrefs.edit()
            .putString("base_url_$provider", baseUrl)
            .putString("model_name_$provider", modelName)
            .apply()
    }

    fun getProviderConfig(provider: String): Pair<String, String> {
        val baseUrl = encryptedPrefs.getString("base_url_$provider", "") ?: ""
        val modelName = encryptedPrefs.getString("model_name_$provider", "") ?: ""
        return Pair(baseUrl, modelName)
    }

    fun saveTokenBudget(budget: Int) {
        encryptedPrefs.edit().putInt("token_budget", budget).apply()
    }

    fun getTokenBudget(): Int {
        return encryptedPrefs.getInt("token_budget", 0)
    }

    fun saveGitToken(provider: String, token: String) {
        encryptedPrefs.edit().putString("git_token_$provider", token).apply()
    }

    fun getGitToken(provider: String): String {
        return encryptedPrefs.getString("git_token_$provider", "") ?: ""
    }

    fun saveMaxIterations(max: Int) {
        encryptedPrefs.edit().putInt("max_iterations", max).apply()
    }

    fun getMaxIterations(): Int {
        return encryptedPrefs.getInt("max_iterations", 50)
    }
}
