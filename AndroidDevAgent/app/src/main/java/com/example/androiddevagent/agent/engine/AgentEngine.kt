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
    private val eventStream: EventStream,
    private val stuckDetector: StuckDetector
) {

    private val maxIterations = 20
    private val maxAutoFixAttempts = 3

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
        var consecutiveBuildFailures = 0

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

                    if (toolName == "gradle_build") {
                        val buildSuccess = result.success
                        eventStream.emit(AgentEvent.BuildResultEvent(
                            success = buildSuccess,
                            output = result.output
                        ))

                        if (buildSuccess) {
                            consecutiveBuildFailures = 0
                        } else {
                            consecutiveBuildFailures++
                            if (consecutiveBuildFailures >= maxAutoFixAttempts) {
                                eventStream.emit(AgentEvent.StuckDetectedEvent(
                                    "Build has failed $consecutiveBuildFailures times in a row. " +
                                    "Auto-fix attempts exhausted."
                                ))
                                break
                            }
                            eventStream.emit(AgentEvent.AutoFixEvent(
                                attempt = consecutiveBuildFailures,
                                maxAttempts = maxAutoFixAttempts,
                                errorSummary = result.output.take(300)
                            ))
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

            val stuckState = stuckDetector.detect(eventStream.history, iterations)
            if (stuckState.isStuck) {
                when (stuckState.strategy) {
                    StuckStrategy.SWITCH_APPROACH -> {
                        messages.add(ChatCompletionRequest.Message(
                            role = "user",
                            content = "[System] You appear to be stuck: ${stuckState.reason}\n" +
                                     "Please try a completely different approach. " +
                                     "Do not repeat the same action."
                        ))
                    }
                    StuckStrategy.ASK_USER -> {
                        eventStream.emit(AgentEvent.StuckDetectedEvent(stuckState.reason))
                        break
                    }
                    StuckStrategy.ABORT -> {
                        eventStream.emit(AgentEvent.StuckDetectedEvent(stuckState.reason))
                        break
                    }
                    StuckStrategy.NONE -> { }
                }
            }
        }

        if (iterations >= maxIterations) {
            eventStream.emit(AgentEvent.StuckDetectedEvent("Reached maximum iterations ($maxIterations)"))
        }
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
}
