package com.example.androiddevagent.agent.security

import com.example.androiddevagent.agent.llm.ChatCompletionRequest
import com.example.androiddevagent.agent.skills.SkillManager
import javax.inject.Inject
import javax.inject.Singleton

enum class SecurityLevel {
    AUTO_CONFIRM,
    DANGEROUS_CONFIRM,
    ALL_CONFIRM
}

@Singleton
class SecurityPolicy @Inject constructor(
    private val skillManager: SkillManager
) {

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
        val skillTool = skillManager.findSkillByToolName(toolCall.function.name)
        if (skillTool != null) {
            return when (skillTool.riskLevel) {
                "high" -> true
                "medium" -> level != SecurityLevel.AUTO_CONFIRM
                "low" -> level == SecurityLevel.ALL_CONFIRM
                else -> true
            }
        }
        return when (level) {
            SecurityLevel.AUTO_CONFIRM -> false
            SecurityLevel.DANGEROUS_CONFIRM -> toolCall.function.name in dangerousTools
            SecurityLevel.ALL_CONFIRM -> toolCall.function.name in allTools
        }
    }

    fun needsConfirmation(toolName: String): Boolean {
        val skillTool = skillManager.findSkillByToolName(toolName)
        if (skillTool != null) {
            return when (skillTool.riskLevel) {
                "high" -> true
                "medium" -> level != SecurityLevel.AUTO_CONFIRM
                "low" -> level == SecurityLevel.ALL_CONFIRM
                else -> true
            }
        }
        return when (level) {
            SecurityLevel.AUTO_CONFIRM -> false
            SecurityLevel.DANGEROUS_CONFIRM -> toolName in dangerousTools
            SecurityLevel.ALL_CONFIRM -> toolName in allTools
        }
    }

    fun getRiskDescription(toolCall: ChatCompletionRequest.ToolCall): String {
        val skillTool = skillManager.findSkillByToolName(toolCall.function.name)
        if (skillTool != null) {
            val riskDesc = when (skillTool.riskLevel) {
                "high" -> "高风险技能操作"
                "medium" -> "中等风险技能操作"
                "low" -> "低风险技能操作"
                else -> "未知风险技能操作"
            }
            val accessDesc = buildString {
                if (skillTool.networkAccess) append("需要网络访问; ")
                if (skillTool.fileAccess != "none") append("文件访问: ${skillTool.fileAccess}; ")
            }
            return "技能 ${skillTool.name}: $riskDesc。$accessDesc${skillTool.description}"
        }
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
