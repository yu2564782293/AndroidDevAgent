package com.example.androiddevagent.agent.skills

import com.example.androiddevagent.agent.llm.ChatCompletionRequest
import com.example.androiddevagent.agent.tools.ToolResult
import com.example.androiddevagent.data.SkillDao
import com.example.androiddevagent.data.SkillEntity
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillRuntime @Inject constructor(
    private val skillDao: SkillDao
) {

    private val scriptCache = mutableMapOf<String, String>()

    suspend fun executeTool(
        skillId: String,
        toolName: String,
        args: Map<String, String>,
        projectPath: String
    ): ToolResult {
        val skill = skillDao.getById(skillId)
            ?: return ToolResult("技能未找到: $skillId", false)

        if (!skill.enabled) {
            return ToolResult("技能已禁用: ${skill.name}", false)
        }

        val manifest = SkillManifestParser.parse(skill.manifestJson)
            .getOrElse { return ToolResult("技能清单解析失败: ${it.message}", false) }

        val toolDef = manifest.tools.find { it.name == toolName }
            ?: return ToolResult("技能 $skillId 中未找到工具: $toolName", false)

        return when (skill.runtimeType) {
            "prompt" -> executePromptTool(skill, toolDef, args, projectPath)
            "script" -> executeScriptTool(skill, toolDef, args, projectPath, manifest.security)
            "hybrid" -> executeHybridTool(skill, toolDef, args, projectPath, manifest.security)
            else -> ToolResult("不支持的运行时类型: ${skill.runtimeType}", false)
        }
    }

    fun getToolDefinitions(skill: SkillEntity): List<ChatCompletionRequest.ToolDefinition> {
        val manifest = SkillManifestParser.parse(skill.manifestJson).getOrNull() ?: return emptyList()

        return manifest.tools.map { tool ->
            ChatCompletionRequest.ToolDefinition(
                function = ChatCompletionRequest.FunctionDef(
                    name = tool.name,
                    description = tool.description,
                    parameters = ChatCompletionRequest.Parameters(
                        properties = tool.parameters.properties.mapValues { (_, prop) ->
                            ChatCompletionRequest.PropertyDef(
                                type = prop.type,
                                description = prop.description
                            )
                        },
                        required = tool.parameters.required
                    )
                )
            )
        }
    }

    fun getKnowledgeContext(skill: SkillEntity): String {
        if (skill.knowledge.isBlank()) return ""
        return "\n## 技能知识: ${skill.name}\n${skill.knowledge}"
    }

    private fun executePromptTool(
        skill: SkillEntity,
        toolDef: SkillToolDef,
        args: Map<String, String>,
        projectPath: String
    ): ToolResult {
        val argsDesc = args.entries.joinToString("\n") { "- ${it.key}: ${it.value.take(200)}" }
        return ToolResult(
            "[技能 ${skill.name} - ${toolDef.name}]\n参数:\n$argsDesc\n\n请基于以上信息和技能知识执行任务。",
            true
        )
    }

    private fun executeScriptTool(
        skill: SkillEntity,
        toolDef: SkillToolDef,
        args: Map<String, String>,
        projectPath: String,
        security: SkillSecurity?
    ): ToolResult {
        val skillDir = getSkillDir(skill.id)
        val scriptFile = File(skillDir, skill.runtimeEntry)

        if (!scriptFile.exists()) {
            return ToolResult("技能脚本不存在: ${scriptFile.absolutePath}", false)
        }

        val scriptContent = scriptCache.getOrPut("${skill.id}:${toolDef.name}") {
            scriptFile.readText()
        }

        return try {
            val argsJson = com.google.gson.Gson().toJson(args)
            val result = executeScript(scriptContent, toolDef.name, argsJson, projectPath, security)
            result
        } catch (e: Exception) {
            ToolResult("技能执行错误: ${e.message}", false)
        }
    }

    private fun executeHybridTool(
        skill: SkillEntity,
        toolDef: SkillToolDef,
        args: Map<String, String>,
        projectPath: String,
        security: SkillSecurity?
    ): ToolResult {
        val scriptResult = executeScriptTool(skill, toolDef, args, projectPath, security)
        if (!scriptResult.success) return scriptResult

        return ToolResult(
            "[技能 ${skill.name} - ${toolDef.name}]\n${scriptResult.output}\n\n请基于以上结果和技能知识继续分析。",
            true
        )
    }

    private fun executeScript(
        scriptContent: String,
        toolName: String,
        argsJson: String,
        projectPath: String,
        security: SkillSecurity?
    ): ToolResult {
        val timeout = security?.maxExecutionTimeMs ?: 30000

        return try {
            val processBuilder = ProcessBuilder("sh", "-c", scriptContent)
            processBuilder.environment()["TOOL_NAME"] = toolName
            processBuilder.environment()["TOOL_ARGS"] = argsJson
            processBuilder.environment()["PROJECT_PATH"] = projectPath
            processBuilder.redirectErrorStream(true)

            val process = processBuilder.start()
            val output = process.inputStream.bufferedReader().readText()
            val completed = process.waitFor(timeout / 1000, java.util.concurrent.TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                return ToolResult("技能执行超时 (${timeout}ms)", false)
            }

            val exitCode = process.exitValue()
            ToolResult(
                if (exitCode == 0) output.take(3000)
                else "脚本执行失败 (退出码: $exitCode)\n${output.take(1000)}",
                exitCode == 0
            )
        } catch (e: Exception) {
            ToolResult("脚本执行异常: ${e.message}", false)
        }
    }

    fun getSkillDir(skillId: String): File {
        return File("/sdcard/DerekAI/skills", skillId.replace("/", "_"))
    }
}
