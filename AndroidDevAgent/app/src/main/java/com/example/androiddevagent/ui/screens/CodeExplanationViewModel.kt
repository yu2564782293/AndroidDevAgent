package com.example.androiddevagent.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddevagent.agent.LLMProvider
import com.example.androiddevagent.agent.llm.LlmProvider
import com.example.androiddevagent.data.dao.ConversationDao
import com.example.androiddevagent.data.entity.Conversation
import com.example.androiddevagent.models.ProgrammingLanguage
import com.example.androiddevagent.utils.InputValidator
import com.example.androiddevagent.utils.RateLimitResult
import com.example.androiddevagent.utils.RateLimiter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class CodeExplanationViewModel @Inject constructor(
    private val llmProvider: LLMProvider,
    private val llmProvider: LlmProvider,
    private val conversationDao: ConversationDao,
    private val rateLimiter: RateLimiter
) : ViewModel() {

    private val _uiState = MutableStateFlow(CodeExplanationUiState())
    val uiState: StateFlow<CodeExplanationUiState> = _uiState.asStateFlow()

    private var explanationJob: Job? = null

    fun explainCode(code: String, language: ProgrammingLanguage) {
        val sanitizedInput = InputValidator.sanitizeUserInput(code)
        val trimmedCode = sanitizedInput.value
        if (trimmedCode.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请先粘贴需要解释的代码") }
            return
        }

        val rateLimitWarning = when (val rateLimitResult = rateLimiter.tryAcquire(ACTION_KEY)) {
            is RateLimitResult.Allowed -> rateLimitResult.warningMessage
            is RateLimitResult.Blocked -> {
                _uiState.update { it.copy(errorMessage = rateLimitResult.message) }
                return
            }
        }

        explanationJob?.cancel()
        explanationJob = viewModelScope.launch {
            _uiState.value = CodeExplanationUiState(
                isLoading = true,
                loadingMessage = if (sanitizedInput.wasTruncated) {
                    "输入内容已截断至 ${sanitizedInput.maxLength} 字符，正在解释代码..."
                } else {
                    "正在解释代码..."
                },
                errorMessage = rateLimitWarning
            )

            try {
                val responseBuilder = StringBuilder()

                llmProvider.streamCompletion(
                    buildCodeExplanationPrompt(trimmedCode, language)
                ).collect { token ->
                    responseBuilder.append(token)
                    _uiState.update {
                        it.copy(
                            explanation = responseBuilder.toString(),
                            errorMessage = null
                        )
                    }
                }

                val response = responseBuilder.toString()
                if (response.isNotBlank()) {
                    saveConversation(trimmedCode, response, language)
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingMessage = null
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingMessage = null,
                        errorMessage = exception.message ?: "代码解释失败"
                    )
                }
            }
        }
    }

    private suspend fun saveConversation(
        code: String,
        response: String,
        language: ProgrammingLanguage
    ) {
        runCatching {
            conversationDao.insert(
                Conversation(
                    screenType = "code_explain",
                    userMessage = code,
                    aiResponse = response,
                    language = language.displayName
                )
            )
        }
    }

    fun clearResult() {
        explanationJob?.cancel()
        _uiState.value = CodeExplanationUiState()
    }

    private fun buildCodeExplanationPrompt(
        code: String,
        language: ProgrammingLanguage
    ): String {
        return """
            系统指令：你是资深 Android 代码审查与教学助手。请用中文解释用户提供的代码，回答要准确、可执行，并明确说明你基于哪些上下文做出判断。

            请严格按以下结构输出：
            ## 逐行解释
            按代码执行顺序解释关键行、关键分支、状态变化和 API 调用。对于简单重复行可以合并说明，但不要跳过影响行为的代码。

            ## 设计模式分析
            识别代码中体现的架构思想、设计模式、职责边界和依赖关系。如果没有明显模式，请说明原因。

            ## 优化建议
            给出可落地的性能、可读性、错误处理、可测试性和 Android 最佳实践建议。

            ## 风险与注意事项
            指出潜在崩溃点、线程问题、生命周期问题、空值风险、资源泄漏或安全风险。

            要求：
            - 保留必要代码片段，代码片段使用 Markdown fenced code block。
            - 对不确定信息明确写出假设，不要编造项目上下文。
            - 语言：${language.displayName}

            用户代码：
            ```${language.extension}
            $code
            ```
        """.trimIndent()
    }

    private companion object {
        const val ACTION_KEY = "code_explanation"
    }
}

data class CodeExplanationUiState(
    val explanation: String = "",
    val isLoading: Boolean = false,
    val loadingMessage: String? = null,
    val errorMessage: String? = null
)
