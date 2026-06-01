package com.example.androiddevagent.agent.llm

import com.example.androiddevagent.agent.tools.ToolDefinitions
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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
        this.baseUrl = baseUrl
        this.modelName = modelName
        this.api = buildApi()
    }

    fun isConfigured(): Boolean = apiKey.isNotEmpty()

    fun getConfig(): Triple<String, String, String> = Triple(apiKey, baseUrl, modelName)

    suspend fun chatWithTools(
        messages: List<ChatCompletionRequest.Message>,
        tools: List<ChatCompletionRequest.ToolDefinition>
    ): ChatCompletionResponse {
        if (!isConfigured()) {
            return simulateResponse(messages, tools)
        }

        return withContext(Dispatchers.IO) {
            try {
                val request = ChatCompletionRequest(
                    model = modelName,
                    messages = messages,
                    tools = tools,
                    temperature = 0.3,
                    maxTokens = 4096
                )

                val currentApi = api ?: buildApi()
                currentApi.createChatCompletion(
                    authorization = "Bearer $apiKey",
                    request = request
                )
            } catch (e: Exception) {
                throw e
            }
        }
    }

    private fun buildApi(): LlmApi {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        return retrofit.create(LlmApi::class.java)
    }

    private fun simulateResponse(
        messages: List<ChatCompletionRequest.Message>,
        tools: List<ChatCompletionRequest.ToolDefinition>
    ): ChatCompletionResponse {
        val lastUserMsg = messages.lastOrNull { it.role == "user" }?.content ?: ""
        val lastToolResult = messages.lastOrNull { it.role == "tool" }?.content

        val content = if (lastToolResult != null) {
            "I see the result. Let me continue working on the task."
        } else {
            "I'll help you with that. Let me start by examining the project structure."
        }

        val toolCall = if (lastToolResult == null) {
            ChatCompletionRequest.ToolCall(
                id = "call_${System.currentTimeMillis()}",
                type = "function",
                function = ChatCompletionRequest.FunctionCall(
                    name = "list_files",
                    arguments = """{"path": "."}"""
                )
            )
        } else {
            ChatCompletionRequest.ToolCall(
                id = "call_${System.currentTimeMillis()}",
                type = "function",
                function = ChatCompletionRequest.FunctionCall(
                    name = "read_file",
                    arguments = """{"path": "build.gradle"}"""
                )
            )
        }

        return ChatCompletionResponse(
            id = "sim_${System.currentTimeMillis()}",
            choices = listOf(
                ChatCompletionResponse.Choice(
                    index = 0,
                    message = ChatCompletionResponse.ResponseMessage(
                        role = "assistant",
                        content = content,
                        toolCalls = listOf(toolCall)
                    ),
                    finishReason = "tool_calls"
                )
            )
        )
    }
}
