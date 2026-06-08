package com.example.androiddevagent.agent.llm

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmProvider @Inject constructor() {

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

    fun isConfigured(): Boolean = apiKey.isNotEmpty()

    fun getConfig(): Triple<String, String, String> = Triple(apiKey, baseUrl, modelName)

    suspend fun chatWithTools(
        messages: List<ChatCompletionRequest.Message>,
        tools: List<ChatCompletionRequest.ToolDefinition>
    ): ChatCompletionResponse {
        return withContext(Dispatchers.IO) {
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
}
