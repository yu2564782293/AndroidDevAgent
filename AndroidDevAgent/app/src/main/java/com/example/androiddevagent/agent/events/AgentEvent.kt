package com.example.androiddevagent.agent.events

sealed class AgentEvent {
    data class UserMessage(val content: String) : AgentEvent()
    data class AssistantThought(val content: String) : AgentEvent()
    data class ToolCallEvent(
        val callId: String,
        val name: String,
        val args: Map<String, String>
    ) : AgentEvent()
    data class ToolResultEvent(
        val callId: String,
        val output: String,
        val success: Boolean
    ) : AgentEvent()
    data class LintResultEvent(
        val path: String,
        val errors: List<String>,
        val passed: Boolean
    ) : AgentEvent()
    data class BuildResultEvent(
        val success: Boolean,
        val output: String
    ) : AgentEvent()
    data class AutoFixEvent(
        val attempt: Int,
        val maxAttempts: Int,
        val errorSummary: String
    ) : AgentEvent()
    data class TaskCompleteEvent(
        val summary: String,
        val filesChanged: List<String>
    ) : AgentEvent()
    data class StuckDetectedEvent(val reason: String) : AgentEvent()
    data class AwaitingConfirmationEvent(
        val callId: String,
        val name: String,
        val args: Map<String, String>
    ) : AgentEvent()
    data class ErrorEvent(val message: String) : AgentEvent()
}
