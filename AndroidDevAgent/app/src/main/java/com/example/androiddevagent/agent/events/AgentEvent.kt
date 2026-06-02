package com.example.androiddevagent.agent.events

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

sealed class AgentEvent {
    abstract fun eventType(): String

    data class UserMessage(val content: String) : AgentEvent() {
        override fun eventType() = "UserMessage"
    }

    data class AssistantThought(val content: String) : AgentEvent() {
        override fun eventType() = "AssistantThought"
    }

    data class ToolCallEvent(
        val callId: String,
        val name: String,
        val args: Map<String, String>
    ) : AgentEvent() {
        override fun eventType() = "ToolCallEvent"
    }

    data class ToolResultEvent(
        val callId: String,
        val output: String,
        val success: Boolean
    ) : AgentEvent() {
        override fun eventType() = "ToolResultEvent"
    }

    data class LintResultEvent(
        val path: String,
        val errors: List<String>,
        val passed: Boolean
    ) : AgentEvent() {
        override fun eventType() = "LintResultEvent"
    }

    data class BuildResultEvent(
        val success: Boolean,
        val output: String
    ) : AgentEvent() {
        override fun eventType() = "BuildResultEvent"
    }

    data class AutoFixEvent(
        val attempt: Int,
        val maxAttempts: Int,
        val errorSummary: String
    ) : AgentEvent() {
        override fun eventType() = "AutoFixEvent"
    }

    data class TaskCompleteEvent(
        val summary: String,
        val filesChanged: List<String>
    ) : AgentEvent() {
        override fun eventType() = "TaskCompleteEvent"
    }

    data class StuckDetectedEvent(val reason: String) : AgentEvent() {
        override fun eventType() = "StuckDetectedEvent"
    }

    data class AwaitingConfirmationEvent(
        val callId: String,
        val name: String,
        val args: Map<String, String>
    ) : AgentEvent() {
        override fun eventType() = "AwaitingConfirmationEvent"
    }

    data class ErrorEvent(val message: String) : AgentEvent() {
        override fun eventType() = "ErrorEvent"
    }

    fun toJson(): String {
        val data = when (this) {
            is UserMessage -> mapOf("content" to content)
            is AssistantThought -> mapOf("content" to content)
            is ToolCallEvent -> mapOf("callId" to callId, "name" to name, "args" to args)
            is ToolResultEvent -> mapOf("callId" to callId, "output" to output, "success" to success)
            is LintResultEvent -> mapOf("path" to path, "errors" to errors, "passed" to passed)
            is BuildResultEvent -> mapOf("success" to success, "output" to output)
            is AutoFixEvent -> mapOf("attempt" to attempt, "maxAttempts" to maxAttempts, "errorSummary" to errorSummary)
            is TaskCompleteEvent -> mapOf("summary" to summary, "filesChanged" to filesChanged)
            is StuckDetectedEvent -> mapOf("reason" to reason)
            is AwaitingConfirmationEvent -> mapOf("callId" to callId, "name" to name, "args" to args)
            is ErrorEvent -> mapOf("message" to message)
        }
        return gson.toJson(data)
    }

    companion object {
        private val gson = Gson()

        fun fromJson(eventType: String, json: String): AgentEvent {
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = gson.fromJson(json, mapType)

            return when (eventType) {
                "UserMessage" -> UserMessage(data["content"] as? String ?: "")
                "AssistantThought" -> AssistantThought(data["content"] as? String ?: "")
                "ToolCallEvent" -> ToolCallEvent(
                    callId = data["callId"] as? String ?: "",
                    name = data["name"] as? String ?: "",
                    args = (data["args"] as? Map<*, *>)?.mapKeys { it.key.toString() }
                        ?.mapValues { it.value.toString() } ?: emptyMap()
                )
                "ToolResultEvent" -> ToolResultEvent(
                    callId = data["callId"] as? String ?: "",
                    output = data["output"] as? String ?: "",
                    success = data["success"] as? Boolean ?: false
                )
                "LintResultEvent" -> LintResultEvent(
                    path = data["path"] as? String ?: "",
                    errors = (data["errors"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                    passed = data["passed"] as? Boolean ?: false
                )
                "BuildResultEvent" -> BuildResultEvent(
                    success = data["success"] as? Boolean ?: false,
                    output = data["output"] as? String ?: ""
                )
                "AutoFixEvent" -> AutoFixEvent(
                    attempt = (data["attempt"] as? Number)?.toInt() ?: 0,
                    maxAttempts = (data["maxAttempts"] as? Number)?.toInt() ?: 0,
                    errorSummary = data["errorSummary"] as? String ?: ""
                )
                "TaskCompleteEvent" -> TaskCompleteEvent(
                    summary = data["summary"] as? String ?: "",
                    filesChanged = (data["filesChanged"] as? List<*>)?.map { it.toString() } ?: emptyList()
                )
                "StuckDetectedEvent" -> StuckDetectedEvent(data["reason"] as? String ?: "")
                "AwaitingConfirmationEvent" -> AwaitingConfirmationEvent(
                    callId = data["callId"] as? String ?: "",
                    name = data["name"] as? String ?: "",
                    args = (data["args"] as? Map<*, *>)?.mapKeys { it.key.toString() }
                        ?.mapValues { it.value.toString() } ?: emptyMap()
                )
                "ErrorEvent" -> ErrorEvent(data["message"] as? String ?: "")
                else -> ErrorEvent("未知事件类型: $eventType")
            }
        }
    }
}
