package com.example.androiddevagent.agent.llm

import com.example.androiddevagent.agent.LLMClient
import com.example.androiddevagent.agent.LLMMessage
import com.example.androiddevagent.data.SecureStorage
import com.example.androiddevagent.settings.LLMConfig
import com.example.androiddevagent.settings.LLMProvider as SettingsLLMProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmProvider @Inject constructor(
    private val secureStorage: SecureStorage,
    private val llmClient: LLMClient
) {

    private var apiKey: String = ""
    private var baseUrl: String = LlmConstants.DEFAULT_BASE_URL
    private var modelName: String = LlmConstants.DEFAULT_MODEL
    private var api: LlmApi? = null
    private val gson: Gson = GsonBuilder().create()

    fun configure(apiKey: String, baseUrl: String, modelName: String) {
        this.apiKey = apiKey
        this.baseUrl = sanitizeBaseUrl(baseUrl)
        this.modelName = modelName
        try {
            this.api = buildApi()
        } catch (e: Exception) {
            // 配置失败不回滚已保存的 key/model，避免丢失用户输入
            android.util.Log.e("LlmProvider", "buildApi 失败: ${e.message}")
        }
    }

    /**
     * 确保 baseUrl 以 / 结尾，否则 Retrofit.Builder().baseUrl() 会抛出 IllegalArgumentException。
     * 同时滤除明显的非法字符。
     */
    private fun sanitizeBaseUrl(url: String): String {
        if (url.isBlank()) return url
        var sanitized = url.trim()
        if (!sanitized.startsWith("http://") && !sanitized.startsWith("https://")) {
            sanitized = "https://$sanitized"
        }
        if (!sanitized.endsWith("/")) {
            sanitized = "$sanitized/"
        }
        return sanitized
    }

    fun isConfigured(): Boolean = apiKey.isNotEmpty() || loadStoredApiKey().isNotEmpty()

    fun getConfig(): Triple<String, String, String> {
        ensureConfiguredFromStorage()
        return Triple(apiKey, baseUrl, modelName)
    }

    fun streamCompletion(prompt: String): Flow<String> {
        val config = currentStreamingConfig()
        return llmClient.streamChatCompletion(
            config = config,
            messages = listOf(
                LLMMessage(
                    role = "system",
                    content = "你是 AndroidDevAgent，专注帮助 Android 开发者进行代码解释、调试和架构设计。"
                ),
                LLMMessage(role = "user", content = prompt)
            )
        )
    }

    suspend fun chatWithTools(
        messages: List<ChatCompletionRequest.Message>,
        tools: List<ChatCompletionRequest.ToolDefinition>
    ): ChatCompletionResponse {
        return withContext(Dispatchers.IO) {
            ensureConfiguredFromStorage()
            val request = ChatCompletionRequest(
                model = modelName,
                messages = messages,
                tools = tools,
                temperature = 0.3,
                maxTokens = 4096
            )

            val currentApi = api ?: buildApi()
            try {
                currentApi.createChatCompletion(
                    authorization = "Bearer $apiKey",
                    request = request
                )
            } catch (e: Exception) {
                throw Exception("API 调用失败 (${modelName}@${baseUrl}): ${e.message}", e)
            }
        }
    }

    private fun buildApi(): LlmApi {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        return retrofit.create(LlmApi::class.java)
    }

    private fun currentStreamingConfig(): LLMConfig {
        ensureConfiguredFromStorage()
        val providerId = secureStorage.getActiveProvider()
        return LLMConfig(
            providerName = providerId.toSettingsProvider().name,
            apiKey = apiKey,
            baseUrl = baseUrl.trimEnd('/'),
            modelName = modelName,
            temperature = 0.7,
            topP = 0.9,
            maxTokens = 2048
        )
    }

    private fun ensureConfiguredFromStorage() {
        if (apiKey.isNotEmpty()) return

        val providerId = secureStorage.getActiveProvider()
        val storedApiKey = secureStorage.getApiKey(providerId)
        if (storedApiKey.isEmpty()) return

        val providerConfig = LlmProviderConfig.BUILT_IN_PROVIDERS.find { it.id == providerId }
        val (storedBaseUrl, storedModelName) = secureStorage.getProviderConfig(providerId)
        configure(
            apiKey = storedApiKey,
            baseUrl = storedBaseUrl.ifBlank { providerConfig?.baseUrl ?: LlmConstants.DEFAULT_BASE_URL },
            modelName = storedModelName.ifBlank { providerConfig?.defaultModel ?: LlmConstants.DEFAULT_MODEL }
        )
    }

    private fun loadStoredApiKey(): String {
        return secureStorage.getApiKey(secureStorage.getActiveProvider())
    }

    private fun String.toSettingsProvider(): SettingsLLMProvider {
        return when (this) {
            "openai" -> SettingsLLMProvider.OPENAI
            "deepseek" -> SettingsLLMProvider.DEEPSEEQ
            "qwen" -> SettingsLLMProvider.QWEN
            "moonshot" -> SettingsLLMProvider.KIMI
            "zhipu" -> SettingsLLMProvider.CHATGLM
            else -> SettingsLLMProvider.CUSTOM
        }
    }
}
