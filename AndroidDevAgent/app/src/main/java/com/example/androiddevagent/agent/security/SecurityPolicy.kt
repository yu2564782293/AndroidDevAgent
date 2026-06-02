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

    var level: SecurityLevel = SecurityLevel.AUTO_CONFIRM

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
            "delete_file" -> "此操作将永久删除文件，无法撤销。"
            "gradle_build" -> "此操作将执行 Gradle 构建，可能需要较长时间并修改构建输出。"
            "run_tests" -> "此操作将运行测试，可能修改测试输出文件。"
            "write_file" -> "此操作将创建或覆盖文件。"
            "edit_file" -> "此操作将修改现有文件。"
            else -> "此操作将在项目上执行。"
        }
    }
}
