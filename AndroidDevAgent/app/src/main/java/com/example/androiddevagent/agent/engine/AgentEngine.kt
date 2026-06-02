package com.example.androiddevagent.agent.engine

import com.example.androiddevagent.agent.events.AgentEvent
import com.example.androiddevagent.agent.events.EventStream
import com.example.androiddevagent.agent.llm.ChatCompletionRequest
import com.example.androiddevagent.agent.llm.LlmConstants
import com.example.androiddevagent.agent.llm.LlmProvider
import com.example.androiddevagent.agent.memory.AndroidSkills
import com.example.androiddevagent.agent.memory.ContextCondenser
import com.example.androiddevagent.agent.memory.ProjectSummaryGenerator
import com.example.androiddevagent.agent.security.SecurityPolicy
import com.example.androiddevagent.agent.tools.ToolDefinitions
import com.example.androiddevagent.agent.tools.ToolExecutor
import com.example.androiddevagent.agent.vcs.GitIntegration
import com.example.androiddevagent.data.SecureStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentEngine @Inject constructor(
    private val llmProvider: LlmProvider,
    private val toolExecutor: ToolExecutor,
    private val eventStream: EventStream,
    private val stuckDetector: StuckDetector,
    private val condenser: ContextCondenser,
    private val projectSummaryGenerator: ProjectSummaryGenerator,
    private val androidSkills: AndroidSkills,
    private val securityPolicy: SecurityPolicy,
    private val gitIntegration: GitIntegration,
    private val secureStorage: SecureStorage
) {

    private val maxAutoFixAttempts = 3
    private var projectSummary = ""

    fun setProjectPath(path: String) {
        toolExecutor.setProjectPath(path)
        gitIntegration.setProjectPath(path)
        projectSummary = try {
            val summary = projectSummaryGenerator.generate(path)
            buildSummaryString(summary)
        } catch (e: Exception) {
            ""
        }
    }

    fun run(task: String): Flow<AgentEvent> = flow {
        eventStream.clear()

        if (!llmProvider.isConfigured()) {
            emit(AgentEvent.ErrorEvent("未配置 API Key，请先在设置中配置 LLM Provider 和 API Key"))
            return@flow
        }

        val skillContext = androidSkills.getRelevantSkills(task)
        val systemPrompt = LlmConstants.buildSystemPrompt() +
                (if (skillContext.isNotEmpty()) "\n\n## Relevant Android Knowledge\n$skillContext" else "") +
                (if (projectSummary.isNotEmpty()) "\n\n## Project Summary\n$projectSummary" else "")

        val messages = mutableListOf<ChatCompletionRequest.Message>()
        messages.add(ChatCompletionRequest.Message(
            role = "system",
            content = systemPrompt
        ))

        messages.add(ChatCompletionRequest.Message(
            role = "user",
            content = task
        ))

        eventStream.emit(AgentEvent.UserMessage(task))

        var iterations = 0
        val filesChanged = mutableListOf<String>()
        var consecutiveBuildFailures = 0
        val maxIterations = secureStorage.getMaxIterations()

        while (iterations++ < maxIterations) {
            val condensed = condenser.condense(
                messages = messages,
                systemPrompt = systemPrompt,
                projectSummary = projectSummary
            )

            val response = try {
                llmProvider.chatWithTools(condensed, ToolDefinitions.allTools())
            } catch (e: Exception) {
                eventStream.emit(AgentEvent.ErrorEvent("LLM 调用失败: ${e.message}"))
                break
            }

            val assistantMessage = response.choices.firstOrNull()?.message

            val assistantContent = assistantMessage?.content ?: ""
            if (assistantContent.isNotBlank()) {
                eventStream.emit(AgentEvent.AssistantThought(assistantContent))
            }

            if (response.hasToolCalls()) {
                val toolCalls = response.getToolCalls()

                messages.add(ChatCompletionRequest.Message(
                    role = "assistant",
                    content = assistantContent.ifBlank { null },
                    toolCalls = toolCalls
                ))

                for (toolCall in toolCalls) {
                    val callId = toolCall.id
                    val toolName = toolCall.function.name
                    val toolArgs = parseToolArgs(toolCall.function.arguments)

                    if (securityPolicy.needsConfirmation(toolCall)) {
                        eventStream.emit(AgentEvent.AwaitingConfirmationEvent(
                            callId = callId,
                            name = toolName,
                            args = toolArgs
                        ))
                    }

                    eventStream.emit(AgentEvent.ToolCallEvent(callId, toolName, toolArgs))

                    val result = try {
                        toolExecutor.execute(toolCall)
                    } catch (e: Exception) {
                        com.example.androiddevagent.agent.tools.ToolResult(
                            "工具执行异常: ${e.message}", false
                        )
                    }
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
                            try {
                                gitIntegration.autoCommit("build: assembleDebug succeeded")
                            } catch (_: Exception) {
                            }
                        } else {
                            consecutiveBuildFailures++
                            if (consecutiveBuildFailures >= maxAutoFixAttempts) {
                                eventStream.emit(AgentEvent.StuckDetectedEvent(
                                    "构建已连续失败 $consecutiveBuildFailures 次，自动修复尝试已用尽。"
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

                    if (result.success && toolName in listOf("write_file", "edit_file")) {
                        try {
                            gitIntegration.autoCommit("$toolName: ${toolArgs["path"]}")
                        } catch (_: Exception) {
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
                if (filesChanged.isNotEmpty()) {
                    try {
                        gitIntegration.autoCommit("task complete: ${task.take(50)}")
                    } catch (_: Exception) {
                    }
                }
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
                            content = "[系统] 检测到您可能陷入循环: ${stuckState.reason}\n" +
                                     "请尝试完全不同的方法，不要重复相同的操作。"
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
            eventStream.emit(AgentEvent.StuckDetectedEvent("已达到最大迭代次数 ($maxIterations)"))
        }
    }

    private fun buildSummaryString(summary: com.example.androiddevagent.agent.memory.ProjectSummary): String {
        val sb = StringBuilder()
        sb.append("项目结构:\n${summary.structure}\n")
        if (summary.keyFiles.isNotEmpty()) {
            sb.append("关键文件:\n")
            summary.keyFiles.take(15).forEach { f ->
                sb.append("- ${f.path}: ${f.summary} (${f.lineCount} 行)\n")
            }
        }
        if (summary.gradleDependencies.isNotEmpty()) {
            sb.append("依赖项:\n${summary.gradleDependencies}\n")
        }
        if (summary.manifestInfo.isNotEmpty()) {
            sb.append("清单文件:\n${summary.manifestInfo}\n")
        }
        return sb.toString()
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
