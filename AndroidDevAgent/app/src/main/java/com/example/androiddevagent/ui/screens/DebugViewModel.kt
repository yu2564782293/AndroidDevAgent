package com.example.androiddevagent.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddevagent.agent.llm.LlmProvider
import com.example.androiddevagent.data.dao.ConversationDao
import com.example.androiddevagent.data.entity.Conversation
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
class DebugViewModel @Inject constructor(
    private val llmProvider: LlmProvider,
    private val conversationDao: ConversationDao,
    private val rateLimiter: RateLimiter
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

    private var analysisJob: Job? = null

    fun analyzeError(errorInfo: String) {
        val sanitizedInput = InputValidator.sanitizeUserInput(errorInfo)
        val trimmedErrorInfo = sanitizedInput.value
        if (trimmedErrorInfo.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请先输入错误描述或粘贴 Logcat") }
            return
        }

        val rateLimitWarning = when (val rateLimitResult = rateLimiter.tryAcquire(ACTION_KEY)) {
            is RateLimitResult.Allowed -> rateLimitResult.warningMessage
            is RateLimitResult.Blocked -> {
                _uiState.update { it.copy(errorMessage = rateLimitResult.message) }
                return
            }
        }

        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            _uiState.value = DebugUiState(
                isLoading = true,
                loadingMessage = if (sanitizedInput.wasTruncated) {
                    "输入内容已截断至 ${sanitizedInput.maxLength} 字符，正在分析错误..."
                } else {
                    "正在分析错误..."
                },
                errorMessage = rateLimitWarning
            )

            try {
                val responseBuilder = StringBuilder()

                llmProvider.streamCompletion(
                    buildDebugPrompt(trimmedErrorInfo)
                ).collect { token ->
                    responseBuilder.append(token)
                    _uiState.update {
                        it.copy(
                            analysis = responseBuilder.toString(),
                            errorMessage = null
                        )
                    }
                }

                val response = responseBuilder.toString()
                if (response.isNotBlank()) {
                    saveConversation(trimmedErrorInfo, response)
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
                        errorMessage = exception.message ?: "错误分析失败"
                    )
                }
            }
        }
    }

    private suspend fun saveConversation(
        errorInfo: String,
        response: String
    ) {
        runCatching {
            conversationDao.insert(
                Conversation(
                    screenType = "debug",
                    userMessage = errorInfo,
                    aiResponse = response
                )
            )
        }
    }

    fun clearResult() {
        analysisJob?.cancel()
        _uiState.value = DebugUiState()
    }

    private fun buildDebugPrompt(errorInfo: String): String {
        return """
            系统指令：你是资深 Android 调试助手。请分析用户提供的错误描述、异常堆栈或 Logcat，给出可验证、可操作的定位和修复建议。

            请严格使用以下 Markdown 二级标题输出，不要省略任何部分：
            ## 错误类型
            判断错误类别，例如编译错误、运行时崩溃、权限问题、生命周期问题、线程问题、依赖冲突、网络/API 问题等，并说明判断依据。

            ## 根因分析
            结合堆栈、关键日志和 Android 运行机制分析最可能根因。若信息不足，请列出需要补充的日志或代码位置。

            ## 修复方案
            给出按优先级排列的具体修复步骤，必要时提供 Kotlin/Java/Gradle/XML 示例代码。

            ## 预防建议
            给出测试、日志、空值处理、生命周期管理、依赖版本或工程规范方面的预防措施。

            要求：
            - 使用中文回答。
            - 不要凭空假设缺失代码；必要时明确假设。
            - 对高风险操作说明影响范围。

            用户输入：
            ```text
            $errorInfo
            ```
        """.trimIndent()
    }

    private companion object {
        const val ACTION_KEY = "debug_analysis"
    }
}

data class DebugUiState(
    val analysis: String = "",
    val isLoading: Boolean = false,
    val loadingMessage: String? = null,
    val errorMessage: String? = null
)
