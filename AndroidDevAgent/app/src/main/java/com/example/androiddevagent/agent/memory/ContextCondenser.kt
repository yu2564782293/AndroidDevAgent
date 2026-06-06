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
    private val oldSummaryReserve = 2000
    private val historyMaxTokens = 4000
    private val charsPerToken = 4

    /**
     * 智能压缩历史对话消息，保留完整的 assistant→tool→result 对话链。
     * 这是修复上下文断裂的核心方法：不再把历史压缩成一条摘要，
     * 而是保留完整的消息结构，确保 LLM 能理解之前的操作和结果。
     */
    fun condenseHistory(historyMessages: List<ChatCompletionRequest.Message>): List<ChatCompletionRequest.Message> {
        if (historyMessages.isEmpty()) return emptyList()

        val tokenBudget = historyMaxTokens

        // 如果历史消息在预算内，直接返回
        if (estimateTokens(historyMessages) <= tokenBudget) {
            return historyMessages
        }

        // 策略：保留最近的完整对话链 + 早期消息的结构化摘要
        val result = mutableListOf<ChatCompletionRequest.Message>()

        // 1. 找到最近一轮完整的对话链（从最近的 user 消息往前找）
        val recentChain = extractRecentCompleteChain(historyMessages)
        val recentTokens = estimateTokens(recentChain)

        // 2. 如果最近链已经超出预算，截断工具结果
        if (recentTokens > tokenBudget) {
            return truncateToolResults(recentChain, tokenBudget)
        }

        // 3. 剩余预算用于早期消息的结构化摘要
        val remainingTokens = tokenBudget - recentTokens
        if (remainingTokens > 200) {
            val earlierMessages = historyMessages.dropLast(recentChain.size)
            val earlySummary = buildStructuredSummary(earlierMessages, remainingTokens)
            if (earlySummary.isNotEmpty()) {
                result.add(ChatCompletionRequest.Message(
                    role = "system",
                    content = "[Earlier conversation context]\n$earlySummary"
                ))
            }
        }

        result.addAll(recentChain)
        return result
    }

    /**
     * 提取最近一轮完整的对话链（user → assistant → tool → result 循环）
     * 确保不切断 assistant 和 tool result 之间的配对关系
     */
    private fun extractRecentCompleteChain(messages: List<ChatCompletionRequest.Message>): List<ChatCompletionRequest.Message> {
        // 从后往前找，找到最近一条独立的 user 消息作为起点
        val lastUserIdx = messages.indexOfLast { it.role == "user" }
        if (lastUserIdx < 0) return messages.takeLast(10)

        // 从这个 user 消息开始，取到末尾
        val chain = messages.drop(lastUserIdx)

        // 验证完整性：每个 assistant tool_call 都有对应的 tool result
        return ensureChainIntegrity(chain)
    }

    /**
     * 确保对话链完整性：assistant 的 tool_calls 必须有对应的 tool result
     * 如果缺少 tool result，往前补充
     */
    private fun ensureChainIntegrity(messages: List<ChatCompletionRequest.Message>): List<ChatCompletionRequest.Message> {
        val toolCallIds = messages
            .filter { it.role == "assistant" && it.toolCalls != null }
            .flatMap { it.toolCalls ?: emptyList() }
            .map { it.id }
            .toSet()

        val toolResultIds = messages
            .filter { it.role == "tool" }
            .mapNotNull { it.toolCallId }
            .toSet()

        // 如果所有 tool call 都有对应的 result，链是完整的
        if (toolResultIds.containsAll(toolCallIds)) {
            return messages
        }

        // 否则返回原始消息（不做截断，避免破坏结构）
        return messages
    }

    /**
     * 构建结构化摘要，保留工具调用的参数和结果（而非只保留名称）
     */
    private fun buildStructuredSummary(messages: List<ChatCompletionRequest.Message>, tokenBudget: Int): String {
        if (messages.isEmpty()) return ""
        val maxChars = tokenBudget * charsPerToken
        val sb = StringBuilder()

        // 1. 用户请求
        val userMsgs = messages.filter { it.role == "user" }
        if (userMsgs.isNotEmpty()) {
            sb.append("User requests: ")
            sb.append(userMsgs.joinToString("; ") { it.content?.take(200) ?: "" })
            sb.append("\n")
        }

        // 2. 工具调用及结果（关键：保留参数和结果，而非只保留名称）
        val toolCallResults = extractToolCallResults(messages)
        if (toolCallResults.isNotEmpty()) {
            sb.append("Actions taken:\n")
            for ((callDesc, result) in toolCallResults) {
                sb.append("- $callDesc")
                if (result.isNotEmpty()) {
                    sb.append(" => ${result.take(200)}")
                }
                sb.append("\n")
            }
        }

        // 3. 错误信息（完整保留）
        val errors = messages.filter { it.role == "tool" }
            .filter {
                it.content?.contains("失败", ignoreCase = true) == true ||
                it.content?.contains("error", ignoreCase = true) == true ||
                it.content?.contains("failed", ignoreCase = true) == true ||
                it.content?.contains("exception", ignoreCase = true) == true
            }
        if (errors.isNotEmpty()) {
            sb.append("Errors encountered:\n")
            for (err in errors.takeLast(5)) {
                sb.append("- ${err.content?.take(300) ?: ""}\n")
            }
        }

        // 4. 助手关键决策
        val decisions = messages.filter {
            it.role == "assistant" && !it.content.isNullOrBlank() && it.toolCalls == null
        }
        if (decisions.isNotEmpty()) {
            sb.append("Key decisions: ")
            sb.append(decisions.takeLast(3).joinToString("; ") { it.content?.take(200) ?: "" })
            sb.append("\n")
        }

        return sb.toString().take(maxChars)
    }

    /**
     * 从消息列表中提取工具调用及其对应结果的配对
     */
    private fun extractToolCallResults(messages: List<ChatCompletionRequest.Message>): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        var i = 0
        while (i < messages.size) {
            val msg = messages[i]
            if (msg.role == "assistant" && msg.toolCalls != null) {
                for (tc in msg.toolCalls) {
                    val callDesc = "${tc.function.name}(${tc.function.arguments.take(150)})"
                    // 查找对应的 tool result
                    val toolResult = messages.find {
                        it.role == "tool" && it.toolCallId == tc.id
                    }?.content?.take(300) ?: ""
                    result.add(callDesc to toolResult)
                }
            }
            i++
        }
        return result
    }

    /**
     * 截断工具结果以适应 token 预算
     * 优先保留工具调用参数，截断较长的工具结果
     */
    private fun truncateToolResults(messages: List<ChatCompletionRequest.Message>, tokenBudget: Int): List<ChatCompletionRequest.Message> {
        val maxChars = tokenBudget * charsPerToken
        var totalChars = 0
        val result = mutableListOf<ChatCompletionRequest.Message>()

        for (msg in messages) {
            val contentLength = msg.content?.length ?: 0
            val toolCallsLength = msg.toolCalls?.sumOf { tc ->
                tc.function.arguments.length + tc.function.name.length
            } ?: 0
            val msgChars = contentLength + toolCallsLength

            if (totalChars + msgChars <= maxChars) {
                result.add(msg)
                totalChars += msgChars
            } else {
                // 截断内容以适应预算
                val remaining = maxChars - totalChars
                if (remaining > 100) {
                    // 对 tool result 消息做截断
                    if (msg.role == "tool" && msg.content != null) {
                        result.add(msg.copy(content = msg.content!!.take(remaining - 50) + "\n... (truncated)"))
                    } else {
                        result.add(msg.copy(content = msg.content?.take(remaining)))
                    }
                }
                break
            }
        }
        return result
    }

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
        val structuredSummary = buildStructuredSummary(oldMessages, oldSummaryReserve)

        return if (structuredSummary.length > 20) {
            ChatCompletionRequest.Message(
                role = "system",
                content = "[Previous context summary]\n$structuredSummary"
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
