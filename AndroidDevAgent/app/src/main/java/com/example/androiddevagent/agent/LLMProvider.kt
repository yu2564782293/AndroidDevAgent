package com.example.androiddevagent.agent

import com.example.androiddevagent.settings.LLMConfig
import com.example.androiddevagent.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

/**
 * 大模型提供者接口
 * 定义与大模型交互的标准接口
 */
interface LLMProvider {

    /**
     * 生成文本补全
     * @param prompt 提示词
     * @return 模型响应
     */
    suspend fun generateCompletion(prompt: String): String

    /**
     * 流式生成文本补全
     * @param prompt 提示词
     * @return 流式响应
     */
    fun streamCompletion(prompt: String): Flow<String>

    /**
     * 获取模型信息
     * @return 模型信息
     */
    fun getModelInfo(): ModelInfo

    /**
     * 检查模型是否可用
     * @return 是否可用
     */
    suspend fun isAvailable(): Boolean

    /**
     * 设置模型参数
     * @param parameters 参数配置
     */
    fun setParameters(parameters: ModelParameters)

    /**
     * 从设置仓库加载最新配置
     */
    suspend fun configure()
}

/**
 * 模型信息
 */
data class ModelInfo(
    val name: String,
    val version: String,
    val provider: String,
    val capabilities: List<String>,
    val maxTokens: Int
)

/**
 * 模型参数配置
 */
data class ModelParameters(
    val temperature: Double = 0.7,
    val topP: Double = 0.9,
    val maxTokens: Int = 2048,
    val frequencyPenalty: Double = 0.0,
    val presencePenalty: Double = 0.0
)

/**
 * LLM提供者实现类
 * 使用用户自带 API Key 直接访问 OpenAI-compatible Chat Completions 接口。
 */
@Singleton
class LLMProviderImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val llmClient: LLMClient
) : LLMProvider {

    @Volatile
    private var currentConfig: LLMConfig = LLMConfig.DEFAULT

    @Volatile
    private var parameterOverride: ModelParameters? = null

    override suspend fun configure() {
        currentConfig = settingsRepository.configFlow.first()
    }

    override suspend fun generateCompletion(prompt: String): String {
        configure()
        val responseBuilder = StringBuilder()
        streamCompletion(prompt).collect { token ->
            responseBuilder.append(token)
        }
        return responseBuilder.toString()
    }

    override fun streamCompletion(prompt: String): Flow<String> {
        return flow {
            configure()
            val config = currentConfig.withParameters(parameterOverride ?: currentConfig.toModelParameters())
            llmClient.streamChatCompletion(
                config = config,
                messages = listOf(
                    LLMMessage(
                        role = "system",
                        content = "你是 AndroidDevAgent，专注帮助 Android 开发者进行代码生成、代码解释、调试和架构设计。"
                    ),
                    LLMMessage(role = "user", content = prompt)
                )
            ).collect { token ->
                emit(token)
            }
        }
    }

    override fun getModelInfo(): ModelInfo {
        val config = currentConfig
        val activeParameters = parameterOverride ?: config.toModelParameters()
        return ModelInfo(
            name = config.modelName,
            version = "OpenAI-compatible chat completions",
            provider = config.provider.displayName,
            capabilities = listOf(
                "代码生成",
                "代码解释",
                "调试建议",
                "架构设计",
                "流式输出"
            ),
            maxTokens = activeParameters.maxTokens
        )
    }

    override suspend fun isAvailable(): Boolean {
        currentConfig = settingsRepository.configFlow.first()
        return currentConfig.apiKey.isNotBlank() &&
            currentConfig.baseUrl.isNotBlank() &&
            currentConfig.modelName.isNotBlank()
    }

    override fun setParameters(parameters: ModelParameters) {
        this.parameterOverride = parameters
    }

    private fun LLMConfig.withParameters(parameters: ModelParameters): LLMConfig {
        return copy(
            temperature = parameters.temperature,
            topP = parameters.topP,
            maxTokens = parameters.maxTokens
        )
    }

    private fun LLMConfig.toModelParameters(): ModelParameters {
        return ModelParameters(
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens
        )
    }
}
