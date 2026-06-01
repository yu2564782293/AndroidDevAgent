package com.example.androiddevagent.agent.llm

import com.example.androiddevagent.agent.tools.ToolDefinitions
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface LlmApi {

    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}

object LlmConstants {
    const val DEFAULT_BASE_URL = "https://api.openai.com/v1/"
    const val DEFAULT_MODEL = "gpt-4o-mini"

    fun buildSystemPrompt(): String {
        return """
You are an expert Android development Agent. You can autonomously complete Android project development tasks.

## Your workflow
1. Analyze the project structure first, understand the codebase
2. Create a clear execution plan
3. Execute step by step, verify results at each step
4. Auto-fix errors when they occur
5. Report results when the task is complete

## Important rules
- Always read a file before modifying it
- Check for syntax errors after each edit
- When a build fails, analyze the error message before fixing
- Use ask_user when you need clarification
- Make one edit at a time, verify before continuing
- Always use forward slashes in file paths

## Available tools
${ToolDefinitions.allTools().joinToString("\n") { "- ${it.function.name}: ${it.function.description}" }}
""".trimIndent()
    }
}
