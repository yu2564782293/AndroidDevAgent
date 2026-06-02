package com.example.androiddevagent.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddevagent.agent.engine.AgentEngine
import com.example.androiddevagent.agent.llm.LlmConstants
import com.example.androiddevagent.agent.llm.LlmProvider
import com.example.androiddevagent.agent.llm.LlmProviderConfig
import com.example.androiddevagent.agent.llm.TokenTracker
import com.example.androiddevagent.agent.security.SecurityLevel
import com.example.androiddevagent.agent.security.SecurityPolicy
import com.example.androiddevagent.agent.tools.ToolExecutor
import com.example.androiddevagent.data.SecureStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val apiKey: String = "",
    val baseUrl: String = LlmConstants.DEFAULT_BASE_URL,
    val modelName: String = LlmConstants.DEFAULT_MODEL,
    val projectPath: String = "",
    val securityLevel: SecurityLevel = SecurityLevel.AUTO_CONFIRM,
    val saved: Boolean = false,
    val selectedProvider: String = "openai",
    val providers: List<LlmProviderConfig> = LlmProviderConfig.BUILT_IN_PROVIDERS,
    val totalTokens: Long = 0,
    val totalCost: Double = 0.0,
    val todayTokens: Long = 0,
    val todayCost: Double = 0.0,
    val tokenBudget: Int = 0,
    val gitToken: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val llmProvider: LlmProvider,
    private val toolExecutor: ToolExecutor,
    private val securityPolicy: SecurityPolicy,
    private val agentEngine: AgentEngine,
    private val secureStorage: SecureStorage,
    private val tokenTracker: TokenTracker
) : ViewModel() {

    private val prefs by lazy {
        context.getSharedPreferences("agent_settings", Context.MODE_PRIVATE)
    }

    private val _uiState = MutableStateFlow(loadSettings())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val totalUsage = tokenTracker.getTotalUsage()
            val todayUsage = tokenTracker.getTodayUsage()
            _uiState.value = _uiState.value.copy(
                totalTokens = totalUsage.first,
                totalCost = totalUsage.second,
                todayTokens = todayUsage.first,
                todayCost = todayUsage.second
            )
        }
    }

    private fun loadSettings(): SettingsUiState {
        val activeProvider = secureStorage.getActiveProvider()
        val apiKey = secureStorage.getApiKey(activeProvider)
        val (baseUrl, modelName) = secureStorage.getProviderConfig(activeProvider)
        val projectPath = prefs.getString("project_path", "") ?: ""
        val securityLevelName = prefs.getString("security_level", SecurityLevel.AUTO_CONFIRM.name)
            ?: SecurityLevel.AUTO_CONFIRM.name
        val securityLevel = try {
            SecurityLevel.valueOf(securityLevelName)
        } catch (e: Exception) {
            SecurityLevel.AUTO_CONFIRM
        }
        val tokenBudget = secureStorage.getTokenBudget()
        val gitToken = secureStorage.getGitToken("github")

        val effectiveBaseUrl = baseUrl.ifBlank {
            LlmProviderConfig.BUILT_IN_PROVIDERS.find { it.id == activeProvider }?.baseUrl ?: LlmConstants.DEFAULT_BASE_URL
        }
        val effectiveModel = modelName.ifBlank {
            LlmProviderConfig.BUILT_IN_PROVIDERS.find { it.id == activeProvider }?.defaultModel ?: LlmConstants.DEFAULT_MODEL
        }

        if (apiKey.isNotEmpty()) {
            llmProvider.configure(apiKey, effectiveBaseUrl, effectiveModel)
        }
        if (projectPath.isNotEmpty()) {
            agentEngine.setProjectPath(projectPath)
        }
        securityPolicy.level = securityLevel

        return SettingsUiState(
            apiKey = apiKey,
            baseUrl = effectiveBaseUrl,
            modelName = effectiveModel,
            projectPath = projectPath,
            securityLevel = securityLevel,
            selectedProvider = activeProvider,
            tokenBudget = tokenBudget,
            gitToken = gitToken
        )
    }

    fun saveSettings(apiKey: String, baseUrl: String, modelName: String, projectPath: String, securityLevel: SecurityLevel) {
        val provider = _uiState.value.selectedProvider
        secureStorage.saveApiKey(provider, apiKey)
        secureStorage.saveProviderConfig(provider, baseUrl, modelName)
        secureStorage.saveActiveProvider(provider)

        prefs.edit()
            .putString("project_path", projectPath)
            .putString("security_level", securityLevel.name)
            .apply()

        val effectiveBaseUrl = baseUrl.ifBlank {
            LlmProviderConfig.BUILT_IN_PROVIDERS.find { it.id == provider }?.baseUrl ?: LlmConstants.DEFAULT_BASE_URL
        }
        val effectiveModel = modelName.ifBlank {
            LlmProviderConfig.BUILT_IN_PROVIDERS.find { it.id == provider }?.defaultModel ?: LlmConstants.DEFAULT_MODEL
        }

        if (apiKey.isNotEmpty()) {
            llmProvider.configure(apiKey, effectiveBaseUrl, effectiveModel)
        }
        if (projectPath.isNotEmpty()) {
            agentEngine.setProjectPath(projectPath)
        }
        securityPolicy.level = securityLevel

        _uiState.value = _uiState.value.copy(
            apiKey = apiKey,
            baseUrl = effectiveBaseUrl,
            modelName = effectiveModel,
            projectPath = projectPath,
            securityLevel = securityLevel,
            saved = true
        )
    }

    fun selectProvider(providerId: String) {
        val apiKey = secureStorage.getApiKey(providerId)
        val (baseUrl, modelName) = secureStorage.getProviderConfig(providerId)
        val providerConfig = LlmProviderConfig.BUILT_IN_PROVIDERS.find { it.id == providerId }
        val effectiveBaseUrl = baseUrl.ifBlank { providerConfig?.baseUrl ?: "" }
        val effectiveModel = modelName.ifBlank { providerConfig?.defaultModel ?: "" }

        _uiState.value = _uiState.value.copy(
            selectedProvider = providerId,
            apiKey = apiKey,
            baseUrl = effectiveBaseUrl,
            modelName = effectiveModel,
            saved = false
        )
    }

    fun saveTokenBudget(budget: Int) {
        secureStorage.saveTokenBudget(budget)
        _uiState.value = _uiState.value.copy(tokenBudget = budget)
    }

    fun saveGitToken(token: String) {
        secureStorage.saveGitToken("github", token)
        _uiState.value = _uiState.value.copy(gitToken = token)
    }
}
