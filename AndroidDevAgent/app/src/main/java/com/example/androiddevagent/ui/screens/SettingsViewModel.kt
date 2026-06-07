package com.example.androiddevagent.ui.screens

<<<<<<< HEAD
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddevagent.agent.LLMAuthenticationException
import com.example.androiddevagent.agent.LLMClient
import com.example.androiddevagent.agent.LLMNetworkException
import com.example.androiddevagent.agent.LLMRateLimitException
import com.example.androiddevagent.agent.LLMTimeoutException
import com.example.androiddevagent.settings.LLMConfig
import com.example.androiddevagent.settings.LLMProvider
import com.example.androiddevagent.settings.SettingsRepository
import com.example.androiddevagent.utils.InputValidator
import com.example.androiddevagent.utils.errorOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val llmClient: LLMClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
    }

    fun loadConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, successMessage = null) }

            runCatching { settingsRepository.configFlow.first() }
                .onSuccess { config ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            config = config,
                            maxTokensInput = config.maxTokens.toString(),
                            errorMessage = null
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "设置加载失败"
                        )
                    }
                }
        }
    }

    fun onProviderSelected(provider: LLMProvider) {
        _uiState.update { state ->
            val config = state.config.withProviderDefaults(provider)
            state.copy(
                config = config,
                maxTokensInput = config.maxTokens.toString(),
                errorMessage = null,
                successMessage = null
=======
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
    val gitToken: String = "",
    val maxIterations: Int = 50,
    val saveError: String = ""
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

    private val _uiState = MutableStateFlow(
        try {
            loadSettings()
        } catch (e: Exception) {
            SettingsUiState(saveError = "加载设置失败: ${e.message}")
        }
    )
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
>>>>>>> dev-commercial-v2
            )
        }
    }

<<<<<<< HEAD
    fun onApiKeyChanged(apiKey: String) {
        updateConfig { it.copy(apiKey = InputValidator.sanitizeApiKey(apiKey)) }
    }

    fun onBaseUrlChanged(baseUrl: String) {
        updateConfig { it.copy(baseUrl = baseUrl.take(MAX_URL_INPUT_LENGTH)) }
    }

    fun onModelNameChanged(modelName: String) {
        updateConfig { it.copy(modelName = InputValidator.sanitizeModelName(modelName)) }
    }

    fun onTemperatureChanged(temperature: Float) {
        val rounded = (temperature.coerceIn(0f, 2f) * 10).roundToInt() / 10.0
        updateConfig { it.copy(temperature = rounded) }
    }

    fun onMaxTokensChanged(input: String) {
        val digits = input.filter { it.isDigit() }.take(MAX_TOKEN_INPUT_LENGTH)
        val parsedValue = digits.toIntOrNull() ?: 0

        _uiState.update { state ->
            state.copy(
                maxTokensInput = digits,
                config = state.config.copy(maxTokens = parsedValue),
                errorMessage = null,
                successMessage = null
            )
        }
    }

    fun toggleApiKeyVisibility() {
        _uiState.update { it.copy(isApiKeyVisible = !it.isApiKeyVisible) }
    }

    fun saveConfig() {
        viewModelScope.launch {
            val config = _uiState.value.validatedForSave() ?: return@launch

            _uiState.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }

            runCatching { settingsRepository.saveConfig(config) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            config = config,
                            maxTokensInput = config.maxTokens.toString(),
                            successMessage = "设置已保存",
                            errorMessage = null
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            successMessage = null,
                            errorMessage = throwable.message ?: "设置保存失败"
                        )
                    }
                }
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            val config = _uiState.value.validatedForRequest() ?: return@launch

            _uiState.update { it.copy(isTesting = true, errorMessage = null, successMessage = null) }

            runCatching { llmClient.testConnection(config) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            successMessage = "连接测试成功",
                            errorMessage = null
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            successMessage = null,
                            errorMessage = throwable.toUserMessage()
                        )
                    }
                }
        }
    }

    private fun updateConfig(transform: (LLMConfig) -> LLMConfig) {
        _uiState.update { state ->
            state.copy(
                config = transform(state.config),
                errorMessage = null,
                successMessage = null
            )
        }
    }

    private fun SettingsUiState.validatedForSave(): LLMConfig? {
        val parsedMaxTokens = maxTokensInput.toIntOrNull()
        if (parsedMaxTokens == null || parsedMaxTokens <= 0) {
            _uiState.update { it.copy(errorMessage = "最大 Token 数必须大于 0", successMessage = null) }
            return null
        }

        val normalizedConfig = config.copy(
            apiKey = InputValidator.sanitizeApiKey(config.apiKey),
            baseUrl = InputValidator.sanitizeBaseUrl(config.baseUrl),
            modelName = InputValidator.sanitizeModelName(config.modelName),
            maxTokens = parsedMaxTokens
        )

        val validationError = listOf(
            InputValidator.validateApiKey(normalizedConfig.apiKey),
            InputValidator.validateUrl(normalizedConfig.baseUrl),
            InputValidator.validateModelName(normalizedConfig.modelName)
        ).firstNotNullOfOrNull { it.errorOrNull() }

        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError, successMessage = null) }
            return null
        }

        return normalizedConfig
    }

    private fun SettingsUiState.validatedForRequest(): LLMConfig? {
        return validatedForSave()
    }

    private fun Throwable.toUserMessage(): String {
        return when (this) {
            is LLMAuthenticationException -> message
            is LLMRateLimitException -> message
            is LLMTimeoutException,
            is TimeoutCancellationException -> "连接超时，请稍后重试"
            is LLMNetworkException -> message
            else -> message ?: "连接测试失败"
        }
    }

    private companion object {
        const val MAX_TOKEN_INPUT_LENGTH = 7
        const val MAX_URL_INPUT_LENGTH = 512
    }
}

data class SettingsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val isApiKeyVisible: Boolean = false,
    val config: LLMConfig = LLMConfig.DEFAULT,
    val maxTokensInput: String = LLMConfig.DEFAULT.maxTokens.toString(),
    val successMessage: String? = null,
    val errorMessage: String? = null
) {
    val selectedProvider: LLMProvider
        get() = config.provider
}
=======
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
        val maxIterations = secureStorage.getMaxIterations()

        val effectiveBaseUrl = baseUrl.ifBlank {
            LlmProviderConfig.BUILT_IN_PROVIDERS.find { it.id == activeProvider }?.baseUrl ?: LlmConstants.DEFAULT_BASE_URL
        }
        val effectiveModel = modelName.ifBlank {
            LlmProviderConfig.BUILT_IN_PROVIDERS.find { it.id == activeProvider }?.defaultModel ?: LlmConstants.DEFAULT_MODEL
        }

        if (apiKey.isNotEmpty()) {
            try {
                llmProvider.configure(apiKey, effectiveBaseUrl, effectiveModel)
            } catch (e: Exception) {
                // 配置失败不影响页面加载，用户可手动修正后重新保存
            }
        }
        if (projectPath.isNotEmpty()) {
            try {
                agentEngine.setProjectPath(projectPath)
            } catch (e: Exception) {
                // 项目路径无效不影响加载
            }
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
            gitToken = gitToken,
            maxIterations = maxIterations
        )
    }

    fun saveSettings(apiKey: String, baseUrl: String, modelName: String, projectPath: String, securityLevel: SecurityLevel) {
        try {
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
                saved = true,
                saveError = ""
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                saved = false,
                saveError = "保存失败: ${e.message}"
            )
        }
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

    fun saveMaxIterations(max: Int) {
        val clamped = max.coerceIn(5, 500)
        secureStorage.saveMaxIterations(clamped)
        _uiState.value = _uiState.value.copy(maxIterations = clamped)
    }
}
>>>>>>> dev-commercial-v2
