package com.example.androiddevagent.agent.security

import com.example.androiddevagent.agent.llm.ChatCompletionRequest
import javax.inject.Inject
import javax.inject.Singleton

enum class SecurityLevel {
    AUTO_CONFIRM,
    DANGEROUS_CONFIRM,
    ALL_CONFIRM
}

@Singleton
class SecurityPolicy @Inject constructor() {

    var level: SecurityLevel = SecurityLevel.DANGEROUS_CONFIRM

    private val dangerousTools = setOf(
        "delete_file",
        "gradle_build",
        "run_tests",
        "git_revert"
    )

    private val allTools = setOf(
        "read_file",
        "write_file",
        "edit_file",
        "list_files",
        "delete_file",
        "gradle_build",
        "run_tests",
        "read_logcat",
        "lint_check",
        "ask_user"
    )

    fun needsConfirmation(toolCall: ChatCompletionRequest.ToolCall): Boolean {
        return when (level) {
            SecurityLevel.AUTO_CONFIRM -> false
            SecurityLevel.DANGEROUS_CONFIRM -> toolCall.function.name in dangerousTools
            SecurityLevel.ALL_CONFIRM -> toolCall.function.name in allTools
        }
    }

    fun needsConfirmation(toolName: String): Boolean {
        return when (level) {
            SecurityLevel.AUTO_CONFIRM -> false
            SecurityLevel.DANGEROUS_CONFIRM -> toolName in dangerousTools
            SecurityLevel.ALL_CONFIRM -> toolName in allTools
        }
    }

    fun getRiskDescription(toolCall: ChatCompletionRequest.ToolCall): String {
        return when (toolCall.function.name) {
            "delete_file" -> "This will permanently delete a file. This action cannot be undone."
            "gradle_build" -> "This will execute a Gradle build which may take time and modify build outputs."
            "run_tests" -> "This will execute tests which may modify test output files."
            "write_file" -> "This will create or overwrite a file."
            "edit_file" -> "This will modify an existing file."
            else -> "This action will be executed on the project."
        }
    }
}
