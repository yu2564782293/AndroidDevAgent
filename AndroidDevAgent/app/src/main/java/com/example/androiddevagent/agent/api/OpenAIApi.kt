package com.example.androiddevagent.agent.api

import retrofit2.http.Body
import retrofit2.http.POST

interface OpenAIApi {

    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}

data class ChatCompletionRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Double = 0.7,
    val maxTokens: Int = 2048,
    val topP: Double = 0.9,
    val frequencyPenalty: Double = 0.0,
    val presencePenalty: Double = 0.0
) {
    data class Message(
        val role: String,
        val content: String
    )
}

data class ChatCompletionResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage
) {
    data class Choice(
        val index: Int,
        val message: Message,
        val finishReason: String
    )

    data class Message(
        val role: String,
        val content: String
    )

    data class Usage(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int
    )
}
