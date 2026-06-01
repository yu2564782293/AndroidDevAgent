package com.example.androiddevagent.agent

interface LLMProvider {

    suspend fun generateCompletion(prompt: String): String

    fun streamCompletion(prompt: String): Sequence<String>

    fun getModelInfo(): ModelInfo

    suspend fun isAvailable(): Boolean

    fun setParameters(parameters: ModelParameters)
}

data class ModelInfo(
    val name: String,
    val version: String,
    val provider: String,
    val capabilities: List<String>,
    val maxTokens: Int
)

data class ModelParameters(
    val temperature: Double = 0.7,
    val topP: Double = 0.9,
    val maxTokens: Int = 2048,
    val frequencyPenalty: Double = 0.0,
    val presencePenalty: Double = 0.0
)
