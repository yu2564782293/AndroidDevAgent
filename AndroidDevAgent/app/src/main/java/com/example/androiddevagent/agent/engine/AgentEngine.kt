package com.example.androiddevagent.agent.engine

import com.example.androiddevagent.agent.events.AgentEvent
import com.example.androiddevagent.agent.events.EventStream
import com.example.androiddevagent.agent.llm.ChatCompletionRequest
import com.example.androiddevagent.agent.llm.ChatCompletionResponse
import com.example.androiddevagent.agent.llm.LlmConstants
import com.example.androiddevagent.agent.llm.LlmProvider
import com.example.androiddevagent.agent.tools.ToolDefinitions
import com.example.androiddevagent.agent.tools.ToolExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class AgentEngine @Inject constructor(
    private val llmProvider: LlmProvider,
    private val toolExecutor: ToolExecutor,
    private val eventStream: EventStream
) {

    private val maxIterations = 20

    fun run(task: String): Flow<AgentEvent> = flow {
        eventStream.clear()
        val messages = mutableListOf<ChatCompletionRequest.Message>()

        messages.add(ChatCompletionRequest.Message(
            role = "system",
            content = LlmConstants.buildSystemPrompt()
        ))

        messages.add(ChatCompletionRequest.Message(
            role = "user",
            content = task
        ))

        eventStream.emit(AgentEvent.UserMessage(task))

        var iterations = 0
        val filesChanged = mutableListOf<String>()

        while (iterations++ < maxIterations) {
            val response = try {
                llmProvider.chatWithTools(messages, ToolDefinitions.allTools())
            } catch (e: Exception) {
                eventStream.emit(AgentEvent.ErrorEvent("LLM call failed: ${e.message}"))
                break
            }

            val assistantMessage = response.choices.firstOrNull()?.message

            if (assistantMessage?.content != null && assistantMessage.content.isNotBlank()) {
                eventStream.emit(AgentEvent.AssistantThought(assistantMessage.content))
            }

            if (response.hasToolCalls()) {
                val toolCalls = response.getToolCalls()
                val toolCallMessages = toolCalls.map { it.toMessage() }
                messages.add(ChatCompletionRequest.Message(
                    role = "assistant",
                    content = assistantMessage?.content,
                    toolCalls = toolCalls
                ))

                for (toolCall in toolCalls) {
                    val callId = toolCall.id
                    val toolName = toolCall.function.name
                    val toolArgs = parseToolArgs(toolCall.function.arguments)

                    eventStream.emit(AgentEvent.ToolCallEvent(callId, toolName, toolArgs))

                    val result = toolExecutor.execute(toolCall)
                    eventStream.emit(AgentEvent.ToolResultEvent(callId, result.output, result.success))

                    if (result.success && toolName in listOf("write_file", "edit_file")) {
                        val filePath = toolArgs["path"] ?: ""
                        if (filePath.isNotEmpty() && filePath !in filesChanged) {
                            filesChanged.add(filePath)
                        }
                    }

                    messages.add(ChatCompletionRequest.Message(
                        role = "tool",
                        content = result.output,
                        toolCallId = callId
                    ))
                }
            } else {
                val finalContent = response.getTextContent()
                eventStream.emit(AgentEvent.TaskCompleteEvent(
                    summary = finalContent,
                    filesChanged = filesChanged
                ))
                break
            }

            val stuckState = detectStuck(eventStream.history)
            if (stuckState != null) {
                eventStream.emit(AgentEvent.StuckDetectedEvent(stuckState))
                break
            }
        }

        if (iterations >= maxIterations) {
            eventStream.emit(AgentEvent.StuckDetectedEvent("Reached maximum iterations ($maxIterations)"))
        }
    }

    private fun detectStuck(history: List<AgentEvent>): String? {
        val toolCalls = history.filterIsInstance<AgentEvent.ToolCallEvent>()
        if (toolCalls.size >= 3) {
            val last3 = toolCalls.takeLast(3)
            if (last3.distinctBy { it.name + it.args.toString() }.size == 1) {
                return "Agent is repeating the same action: ${last3.first().name}"
            }
        }

        val toolResults = history.filterIsInstance<AgentEvent.ToolResultEvent>()
        if (toolResults.size >= 3) {
            val last3Results = toolResults.takeLast(3)
            if (last3Results.all { !it.success } && last3Results.map { it.output }.distinct().size == 1) {
                return "Agent keeps getting the same error and cannot fix it"
            }
        }

        return null
    }

    private fun parseToolArgs(json: String): Map<String, String> {
        return try {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
            val raw: Map<String, Any> = gson.fromJson(json, type)
            raw.mapValues { it.value.toString() }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun ChatCompletionRequest.ToolCall.toMessage(): ChatCompletionRequest.Message {
        return ChatCompletionRequest.Message(
            role = "assistant",
            content = null,
            toolCalls = listOf(this)
        )
    }
}
