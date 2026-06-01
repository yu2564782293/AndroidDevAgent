package com.example.androiddevagent.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import com.example.androiddevagent.agent.engine.AgentEngine
import com.example.androiddevagent.agent.llm.LlmConstants
import com.example.androiddevagent.agent.llm.LlmProvider
import com.example.androiddevagent.agent.security.SecurityLevel
import com.example.androiddevagent.agent.security.SecurityPolicy
import com.example.androiddevagent.agent.tools.ToolExecutor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class SettingsUiState(
    val apiKey: String = "",
    val baseUrl: String = LlmConstants.DEFAULT_BASE_URL,
    val modelName: String = LlmConstants.DEFAULT_MODEL,
    val projectPath: String = "",
    val securityLevel: SecurityLevel = SecurityLevel.DANGEROUS_CONFIRM,
    val saved: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llmProvider: LlmProvider,
    private val toolExecutor: ToolExecutor,
    private val securityPolicy: SecurityPolicy,
    private val agentEngine: AgentEngine
) : ViewModel() {

    private val prefs by lazy {
        context.getSharedPreferences("agent_settings", Context.MODE_PRIVATE)
    }

    private val _uiState = MutableStateFlow(loadSettings())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun loadSettings(): SettingsUiState {
        val apiKey = prefs.getString("api_key", "") ?: ""
        val baseUrl = prefs.getString("base_url", LlmConstants.DEFAULT_BASE_URL) ?: LlmConstants.DEFAULT_BASE_URL
        val modelName = prefs.getString("model_name", LlmConstants.DEFAULT_MODEL) ?: LlmConstants.DEFAULT_MODEL
        val projectPath = prefs.getString("project_path", "") ?: ""
        val securityLevelName = prefs.getString("security_level", SecurityLevel.DANGEROUS_CONFIRM.name)
            ?: SecurityLevel.DANGEROUS_CONFIRM.name
        val securityLevel = try {
            SecurityLevel.valueOf(securityLevelName)
        } catch (e: Exception) {
            SecurityLevel.DANGEROUS_CONFIRM
        }

        if (apiKey.isNotEmpty()) {
            llmProvider.configure(apiKey, baseUrl, modelName)
        }
        if (projectPath.isNotEmpty()) {
            agentEngine.setProjectPath(projectPath)
        }
        securityPolicy.level = securityLevel

        return SettingsUiState(
            apiKey = apiKey,
            baseUrl = baseUrl,
            modelName = modelName,
            projectPath = projectPath,
            securityLevel = securityLevel
        )
    }

    fun saveSettings(apiKey: String, baseUrl: String, modelName: String, projectPath: String, securityLevel: SecurityLevel) {
        prefs.edit()
            .putString("api_key", apiKey)
            .putString("base_url", baseUrl)
            .putString("model_name", modelName)
            .putString("project_path", projectPath)
            .putString("security_level", securityLevel.name)
            .apply()

        if (apiKey.isNotEmpty()) {
            llmProvider.configure(apiKey, baseUrl, modelName)
        }
        if (projectPath.isNotEmpty()) {
            agentEngine.setProjectPath(projectPath)
        }
        securityPolicy.level = securityLevel

        _uiState.value = SettingsUiState(
            apiKey = apiKey,
            baseUrl = baseUrl,
            modelName = modelName,
            projectPath = projectPath,
            securityLevel = securityLevel,
            saved = true
        )
    }
}
