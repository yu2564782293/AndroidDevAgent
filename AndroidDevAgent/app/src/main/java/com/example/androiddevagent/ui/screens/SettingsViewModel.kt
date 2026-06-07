package com.example.androiddevagent.ui.screens

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
            )
        }
    }

    fun onApiKeyChanged(apiKey: String) {
        updateConfig { it.copy(apiKey = apiKey) }
    }

    fun onBaseUrlChanged(baseUrl: String) {
        updateConfig { it.copy(baseUrl = baseUrl) }
    }

    fun onModelNameChanged(modelName: String) {
        updateConfig { it.copy(modelName = modelName) }
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

        return config.copy(maxTokens = parsedMaxTokens)
    }

    private fun SettingsUiState.validatedForRequest(): LLMConfig? {
        val configForSave = validatedForSave() ?: return null
        val error = when {
            configForSave.apiKey.isBlank() -> "请填写 API Key"
            configForSave.baseUrl.isBlank() -> "请填写 Base URL"
            configForSave.modelName.isBlank() -> "请填写模型名称"
            else -> null
        }

        if (error != null) {
            _uiState.update { it.copy(errorMessage = error, successMessage = null) }
            return null
        }

        return configForSave
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
