package com.example.androiddevagent.agent.skills

import com.google.gson.Gson

object SkillTemplateGenerator {

    fun generateScriptSkill(
        id: String,
        name: String,
        description: String,
        toolName: String,
        toolDescription: String,
        author: String = "local"
    ): String {
        val manifest = SkillManifest(
            id = id,
            name = name,
            version = "1.0.0",
            description = description,
            author = author,
            category = "custom",
            icon = "🔧",
            tags = listOf("custom"),
            source = SkillSource(type = "local", repo = "", branch = ""),
            tools = listOf(
                SkillToolDef(
                    name = toolName,
                    description = toolDescription,
                    parameters = SkillParameters(
                        properties = mapOf(
                            "input" to SkillPropertyDef(type = "string", description = "输入参数")
                        ),
                        required = listOf("input")
                    )
                )
            ),
            knowledge = "## $name\n$description",
            runtime = SkillRuntimeDef(
                type = "script",
                entry = "main.kts",
                language = "kotlin-script"
            ),
            security = SkillSecurity(
                riskLevel = "low",
                autoApprove = true,
                networkAccess = false,
                fileAccess = "read_only",
                maxExecutionTimeMs = 15000
            )
        )

        return Gson().toJson(manifest)
    }

    fun generatePromptSkill(
        id: String,
        name: String,
        description: String,
        knowledge: String,
        author: String = "local"
    ): String {
        val manifest = SkillManifest(
            id = id,
            name = name,
            version = "1.0.0",
            description = description,
            author = author,
            category = "custom",
            icon = "🧠",
            tags = listOf("custom", "prompt"),
            source = SkillSource(type = "local", repo = "", branch = ""),
            tools = listOf(
                SkillToolDef(
                    name = "${id.replace("-", "_")}_consult",
                    description = "咨询 $name 相关知识",
                    parameters = SkillParameters(
                        properties = mapOf(
                            "question" to SkillPropertyDef(type = "string", description = "问题")
                        ),
                        required = listOf("question")
                    )
                )
            ),
            knowledge = knowledge,
            runtime = SkillRuntimeDef(type = "prompt"),
            security = SkillSecurity(
                riskLevel = "low",
                autoApprove = true,
                networkAccess = false,
                fileAccess = "none"
            )
        )

        return Gson().toJson(manifest)
    }

    fun generateScriptTemplate(toolName: String): String {
        val dollar = '${'$'}'
        return """#!/usr/bin/env kotlin
/**
 * DEREK AI 技能脚本模板
 * 工具名: $toolName
 *
 * 环境变量:
 * - TOOL_NAME: 工具名称
 * - TOOL_ARGS: JSON 格式的工具参数
 * - PROJECT_PATH: 项目路径
 */

fun main() {
    val toolName = System.getenv("TOOL_NAME") ?: "${dollar}toolName"
    val argsJson = System.getenv("TOOL_ARGS") ?: "{}"
    val projectPath = System.getenv("PROJECT_PATH") ?: "."

    // TODO: 在此实现工具逻辑
    println("工具 ${dollar}toolName 执行成功")
    println("参数: ${dollar}argsJson")
    println("项目路径: ${dollar}projectPath")
}

main()
"""
    }

    fun generateHybridSkill(
        id: String,
        name: String,
        description: String,
        toolName: String,
        toolDescription: String,
        knowledge: String,
        author: String = "local"
    ): String {
        val manifest = SkillManifest(
            id = id,
            name = name,
            version = "1.0.0",
            description = description,
            author = author,
            category = "custom",
            icon = "⚡",
            tags = listOf("custom", "hybrid"),
            source = SkillSource(type = "local", repo = "", branch = ""),
            tools = listOf(
                SkillToolDef(
                    name = toolName,
                    description = toolDescription,
                    parameters = SkillParameters(
                        properties = mapOf(
                            "input" to SkillPropertyDef(type = "string", description = "输入参数")
                        ),
                        required = listOf("input")
                    )
                )
            ),
            knowledge = knowledge,
            runtime = SkillRuntimeDef(
                type = "hybrid",
                entry = "main.kts",
                language = "kotlin-script"
            ),
            security = SkillSecurity(
                riskLevel = "medium",
                autoApprove = false,
                networkAccess = false,
                fileAccess = "read_only",
                maxExecutionTimeMs = 30000
            )
        )

        return Gson().toJson(manifest)
    }
}
