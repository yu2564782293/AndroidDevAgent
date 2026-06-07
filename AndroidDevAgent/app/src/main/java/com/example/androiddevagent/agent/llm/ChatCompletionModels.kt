package com.example.androiddevagent.agent.llm

import com.google.gson.annotations.SerializedName

data class ChatCompletionRequest(
    val model: String,
    val messages: List<Message>,
    val tools: List<ToolDefinition>? = null,
    @SerializedName("tool_choice")
    val toolChoice: Any = "auto",
    val temperature: Double = 0.3,
    @SerializedName("max_tokens")
    val maxTokens: Int = 4096
) {
    data class Message(
        val role: String,
        val content: String? = null,
        @SerializedName("tool_calls")
        val toolCalls: List<ToolCall>? = null,
        @SerializedName("tool_call_id")
        val toolCallId: String? = null,
        val name: String? = null
    )

    data class ToolDefinition(
        val type: String = "function",
        val function: FunctionDef
    )

    data class FunctionDef(
        val name: String,
        val description: String,
        val parameters: Parameters
    )

    data class Parameters(
        val type: String = "object",
        val properties: Map<String, PropertyDef>,
        val required: List<String> = emptyList()
    )

    data class PropertyDef(
        val type: String,
        val description: String
    )

    data class ToolCall(
        val id: String,
        val type: String = "function",
        val function: FunctionCall
    )

    data class FunctionCall(
        val name: String,
        val arguments: String
    )
}

data class ChatCompletionResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage? = null
) {
    data class Choice(
        val index: Int,
        val message: ResponseMessage,
        @SerializedName("finish_reason")
        val finishReason: String?
    )

    data class ResponseMessage(
        val role: String,
        val content: String?,
        @SerializedName("tool_calls")
        val toolCalls: List<ChatCompletionRequest.ToolCall>?
    )

    data class Usage(
        @SerializedName("prompt_tokens")
        val promptTokens: Int,
        @SerializedName("completion_tokens")
        val completionTokens: Int,
        @SerializedName("total_tokens")
        val totalTokens: Int
    )

    fun hasToolCalls(): Boolean {
        return choices.firstOrNull()?.message?.toolCalls?.isNotEmpty() == true
    }

    fun getTextContent(): String {
        return choices.firstOrNull()?.message?.content ?: ""
    }

    fun getToolCalls(): List<ChatCompletionRequest.ToolCall> {
        return choices.firstOrNull()?.message?.toolCalls ?: emptyList()
    }
}
