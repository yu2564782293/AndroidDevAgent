package com.example.androiddevagent.agent.memory

import com.example.androiddevagent.agent.events.AgentEvent
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
    private val systemPromptReserve = 1000
    private val projectSummaryReserve = 1500
    private val recentEventsReserve = 4000
    private val oldSummaryReserve = 1000
    private val charsPerToken = 4

    fun condense(
        events: List<AgentEvent>,
        systemPrompt: String,
        projectSummary: String,
        currentFileContent: String = ""
    ): CondensedContext {
        val messages = mutableListOf<ChatCompletionRequest.Message>()

        messages.add(ChatCompletionRequest.Message(
            role = "system",
            content = buildCondensedSystemPrompt(systemPrompt, projectSummary)
        ))

        val recentEvents = events.takeLast(20)
        val oldEvents = events.dropLast(20)

        if (oldEvents.isNotEmpty()) {
            val oldSummary = summarizeOldEvents(oldEvents)
            messages.add(ChatCompletionRequest.Message(
                role = "system",
                content = "[Previous context summary]\n$oldSummary"
            ))
        }

        for (event in recentEvents) {
            val msg = eventToMessage(event) ?: continue
            messages.add(msg)
        }

        if (currentFileContent.isNotEmpty()) {
            val truncated = truncateToTokens(currentFileContent, 2000)
            messages.add(ChatCompletionRequest.Message(
                role = "system",
                content = "[Current file being edited]\n$truncated"
            ))
        }

        val totalTokens = estimateTokens(messages)
        return CondensedContext(messages, totalTokens)
    }

    private fun buildCondensedSystemPrompt(systemPrompt: String, projectSummary: String): String {
        val sb = StringBuilder()
        sb.append(systemPrompt)
        if (projectSummary.isNotEmpty()) {
            sb.append("\n\n## Project Summary\n")
            sb.append(truncateToTokens(projectSummary, projectSummaryReserve))
        }
        return sb.toString()
    }

    private fun summarizeOldEvents(events: List<AgentEvent>): String {
        val sb = StringBuilder()
        val toolCalls = events.filterIsInstance<AgentEvent.ToolCallEvent>()
        val toolResults = events.filterIsInstance<AgentEvent.ToolResultEvent>()
        val thoughts = events.filterIsInstance<AgentEvent.AssistantThought>()

        if (thoughts.isNotEmpty()) {
            sb.append("Agent thoughts: ")
            sb.append(thoughts.last().content.take(200))
            sb.append("\n")
        }

        if (toolCalls.isNotEmpty()) {
            sb.append("Actions taken: ")
            val actionSummary = toolCalls.takeLast(5).joinToString(", ") { call ->
                val argsStr = call.args.entries.take(2).joinToString(", ") { "${it.key}=${it.value.take(20)}" }
                "${call.name}($argsStr)"
            }
            sb.append(actionSummary)
            sb.append("\n")
        }

        val failures = toolResults.filter { !it.success }
        if (failures.isNotEmpty()) {
            sb.append("Errors encountered: ")
            sb.append(failures.takeLast(3).joinToString("; ") { it.output.take(100) })
            sb.append("\n")
        }

        return sb.toString()
    }

    private fun eventToMessage(event: AgentEvent): ChatCompletionRequest.Message? {
        return when (event) {
            is AgentEvent.UserMessage -> ChatCompletionRequest.Message(
                role = "user",
                content = event.content
            )
            is AgentEvent.AssistantThought -> ChatCompletionRequest.Message(
                role = "assistant",
                content = event.content
            )
            is AgentEvent.ToolCallEvent -> {
                val argsJson = event.args.entries.joinToString(", ") {
                    "\"${it.key}\": \"${it.value.take(100)}\""
                }
                ChatCompletionRequest.Message(
                    role = "assistant",
                    content = "[Tool call: ${event.name} {$argsJson}]"
                )
            }
            is AgentEvent.ToolResultEvent -> ChatCompletionRequest.Message(
                role = "user",
                content = "[Tool result (${if (event.success) "success" else "failed"})]: ${event.output.take(500)}"
            )
            is AgentEvent.BuildResultEvent -> ChatCompletionRequest.Message(
                role = "user",
                content = "[Build ${if (event.success) "succeeded" else "failed"}]: ${event.output.take(300)}"
            )
            is AgentEvent.AutoFixEvent -> ChatCompletionRequest.Message(
                role = "user",
                content = "[Auto-fix attempt ${event.attempt}/${event.maxAttempts}]: ${event.errorSummary.take(200)}"
            )
            else -> null
        }
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
            contentLength / charsPerToken
        }
    }
}
