package com.example.androiddevagent.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androiddevagent.agent.LLMProvider
import com.example.androiddevagent.data.dao.ConversationDao
import com.example.androiddevagent.data.entity.Conversation
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
class ArchitectureViewModel @Inject constructor(
    private val llmProvider: LLMProvider,
    private val conversationDao: ConversationDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArchitectureUiState())
    val uiState: StateFlow<ArchitectureUiState> = _uiState.asStateFlow()

    private var proposalJob: Job? = null

    fun designArchitecture(requirements: String, projectType: String) {
        val trimmedRequirements = requirements.trim()
        if (trimmedRequirements.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请先输入项目描述或需求") }
            return
        }

        proposalJob?.cancel()
        proposalJob = viewModelScope.launch {
            _uiState.value = ArchitectureUiState(
                isLoading = true,
                loadingMessage = "正在设计架构..."
            )

            try {
                val responseBuilder = StringBuilder()

                llmProvider.streamCompletion(
                    buildArchitecturePrompt(trimmedRequirements, projectType)
                ).collect { token ->
                    responseBuilder.append(token)
                    _uiState.update {
                        it.copy(
                            proposal = responseBuilder.toString(),
                            errorMessage = null
                        )
                    }
                }

                val response = responseBuilder.toString()
                if (response.isNotBlank()) {
                    saveConversation(trimmedRequirements, response, projectType)
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
                        errorMessage = exception.message ?: "架构设计失败"
                    )
                }
            }
        }
    }

    private suspend fun saveConversation(
        requirements: String,
        response: String,
        projectType: String
    ) {
        runCatching {
            conversationDao.insert(
                Conversation(
                    screenType = "architecture",
                    userMessage = "项目类型：$projectType\n\n$requirements",
                    aiResponse = response,
                    language = projectType
                )
            )
        }
    }

    fun clearResult() {
        proposalJob?.cancel()
        _uiState.value = ArchitectureUiState()
    }

    private fun buildArchitecturePrompt(requirements: String, projectType: String): String {
        return """
            系统指令：你是资深 Android 架构师。请根据用户需求设计可落地、可演进的 Android 工程架构，重点关注模块边界、依赖方向、数据流、测试和长期维护成本。

            项目类型：$projectType

            请严格使用以下 Markdown 二级标题输出，不要省略任何部分：
            ## 模块拆分
            给出模块列表、职责说明、依赖方向和关键包结构建议。

            ## 数据流
            说明 UI、状态管理、业务逻辑、数据源、缓存和网络层之间的数据流，包含错误态和加载态处理。

            ## 推荐技术栈
            结合 Android 生态给出 UI、依赖注入、异步、持久化、网络、导航、测试、构建和日志方案。

            ## 架构图描述
            用文字描述可绘制的架构图，包括节点、箭头方向和层级关系。

            ## 落地步骤
            给出分阶段实施计划、关键风险和验证方式。

            要求：
            - 使用中文回答。
            - 避免过度设计，明确哪些方案适合当前规模。
            - 对不确定需求写出假设和需要追问的问题。

            用户需求：
            ```text
            $requirements
            ```
        """.trimIndent()
    }
}

data class ArchitectureUiState(
    val proposal: String = "",
    val isLoading: Boolean = false,
    val loadingMessage: String? = null,
    val errorMessage: String? = null
)
