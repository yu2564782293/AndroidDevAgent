package com.example.androiddevagent.agent.memory

import com.example.androiddevagent.agent.llm.ChatCompletionRequest
import javax.inject.Inject
import javax.inject.Singleton

data class CondensedContext(
    val messages: List<ChatCompletionRequest.Message>,
    val tokenEstimate: Int
)

@Singleton
class ContextCondenser @Inject constructor() {

    private val maxContextTokens = 8000
    private val systemPromptReserve = 1500
    private val projectSummaryReserve = 1500
    private val recentMessagesReserve = 4000
    private val oldSummaryReserve = 1000
    private val charsPerToken = 4

    fun condense(
        messages: List<ChatCompletionRequest.Message>,
        systemPrompt: String,
        projectSummary: String,
        currentFileContent: String = ""
    ): List<ChatCompletionRequest.Message> {
        val systemMsg = ChatCompletionRequest.Message(
            role = "system",
            content = buildCondensedSystemPrompt(systemPrompt, projectSummary)
        )

        val nonSystemMessages = messages.filter { it.role != "system" }

        if (estimateTokens(nonSystemMessages) <= maxContextTokens) {
            return listOf(systemMsg) + nonSystemMessages
        }

        val result = mutableListOf<ChatCompletionRequest.Message>()
        result.add(systemMsg)

        val summaryMsg = summarizeOldMessages(nonSystemMessages)
        if (summaryMsg != null) {
            result.add(summaryMsg)
        }

        val recentMessages = takeRecentCompleteExchange(nonSystemMessages)
        result.addAll(recentMessages)

        if (currentFileContent.isNotEmpty()) {
            val truncated = truncateToTokens(currentFileContent, 2000)
            result.add(ChatCompletionRequest.Message(
                role = "system",
                content = "[Current file being edited]\n$truncated"
            ))
        }

        return result
    }

    private fun buildCondensedSystemPrompt(systemPrompt: String, projectSummary: String): String {
        val sb = StringBuilder()
        sb.append(truncateToTokens(systemPrompt, systemPromptReserve))
        if (projectSummary.isNotEmpty()) {
            sb.append("\n\n## Project Summary\n")
            sb.append(truncateToTokens(projectSummary, projectSummaryReserve))
        }
        return sb.toString()
    }

    private fun summarizeOldMessages(messages: List<ChatCompletionRequest.Message>): ChatCompletionRequest.Message? {
        if (messages.size <= 10) return null

        val oldMessages = messages.dropLast(10)
        val sb = StringBuilder()
        sb.append("[Previous context summary]\n")

        val toolCalls = oldMessages.filter { it.role == "assistant" && it.toolCalls != null }
            .flatMap { it.toolCalls ?: emptyList() }
        if (toolCalls.isNotEmpty()) {
            sb.append("Actions taken: ")
            sb.append(toolCalls.takeLast(5).joinToString(", ") { call ->
                "${call.function.name}(...)"
            })
            sb.append("\n")
        }

        val toolResults = oldMessages.filter { it.role == "tool" }
        val failures = toolResults.filter {
            it.content?.contains("失败", ignoreCase = true) == true ||
            it.content?.contains("error", ignoreCase = true) == true ||
            it.content?.contains("failed", ignoreCase = true) == true
        }
        if (failures.isNotEmpty()) {
            sb.append("Errors encountered: ")
            sb.append(failures.takeLast(3).joinToString("; ") {
                it.content?.take(100) ?: ""
            })
            sb.append("\n")
        }

        val userMessages = oldMessages.filter { it.role == "user" }
        if (userMessages.isNotEmpty()) {
            sb.append("User requests: ")
            sb.append(userMessages.last().content?.take(200) ?: "")
            sb.append("\n")
        }

        val summary = sb.toString()
        return if (summary.length > 20) {
            ChatCompletionRequest.Message(
                role = "system",
                content = truncateToTokens(summary, oldSummaryReserve)
            )
        } else {
            null
        }
    }

    private fun takeRecentCompleteExchange(messages: List<ChatCompletionRequest.Message>): List<ChatCompletionRequest.Message> {
        val recent = messages.takeLast(20)

        val firstAssistantIdx = recent.indexOfFirst { it.role == "assistant" }
        if (firstAssistantIdx > 0) {
            return recent.drop(firstAssistantIdx)
        }

        return recent
    }

    private fun truncateToTokens(text: String, maxTokens: Int): String {
        val maxChars = maxTokens * charsPerToken
        return if (text.length > maxChars) {
            text.take(maxChars) + "\n... (truncated)"
        } else {
            text
        }
    }

    private fun estimateTokens(messages: List<ChatCompletionRequest.Message>): Int {
        return messages.sumOf { msg ->
            val contentLength = (msg.content?.length ?: 0)
            val toolCallsLength = msg.toolCalls?.sumOf { tc ->
                tc.function.arguments.length + tc.function.name.length
            } ?: 0
            (contentLength + toolCallsLength) / charsPerToken
        }
    }
}
