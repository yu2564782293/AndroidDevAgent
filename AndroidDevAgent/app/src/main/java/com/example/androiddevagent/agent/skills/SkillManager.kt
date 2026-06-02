package com.example.androiddevagent.agent.skills

import com.example.androiddevagent.agent.llm.ChatCompletionRequest
import com.example.androiddevagent.agent.tools.ToolResult
import com.example.androiddevagent.data.SkillDao
import com.example.androiddevagent.data.SkillEntity

class SkillManager(
    private val skillDao: SkillDao,
    private val skillRuntime: SkillRuntime,
    private val skillInstaller: SkillInstaller,
    private val skillRegistry: SkillRegistry
) {

    fun getAllSkillToolDefinitions(): List<ChatCompletionRequest.ToolDefinition> {
        val skills = getEnabledSkills()
        return skills.flatMap { skill ->
            skillRuntime.getToolDefinitions(skill)
        }
    }

    fun getAllSkillKnowledge(): String {
        val skills = getEnabledSkills()
        return skills.mapNotNull { skill ->
            skillRuntime.getKnowledgeContext(skill)
        }.joinToString("\n")
    }

    suspend fun executeSkillTool(
        toolName: String,
        args: Map<String, String>,
        projectPath: String
    ): ToolResult {
        val skills = skillDao.getEnabledSkills()
        val skill = skills.find { toolName in it.toolNames }
            ?: return ToolResult("未找到提供工具 '$toolName' 的技能", false)

        return skillRuntime.executeTool(skill.id, toolName, args, projectPath)
    }

    suspend fun autoInstallForTask(task: String): List<SkillEntity> {
        val results = skillRegistry.searchForTask(task)
        val installed = mutableListOf<SkillEntity>()

        for (result in results.take(2)) {
            if (!result.installed) {
                val installResult = when {
                    result.sourceUrl.contains("github.com") -> {
                        val repo = result.sourceUrl.removePrefix("https://github.com/")
                        skillInstaller.installFromGitHub(repo)
                    }
                    else -> skillInstaller.installFromUrl(result.sourceUrl)
                }
                installResult.getOrNull()?.let { installed.add(it) }
            }
        }

        return installed
    }

    fun getEnabledSkills(): List<SkillEntity> {
        return try {
            kotlinx.coroutines.runBlocking { skillDao.getEnabledSkills() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getInstalledSkills(): List<SkillEntity> {
        return try {
            kotlinx.coroutines.runBlocking { skillDao.getAll() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun searchSkills(query: String): List<SkillSearchResult> {
        val githubResults = skillRegistry.searchGitHub(query)
        val marketResults = skillRegistry.searchMarketplace(query)
        val allResults = (githubResults + marketResults).distinctBy { it.id }

        val installedIds = skillDao.getAll().map { it.id }.toSet()
        return allResults.map { it.copy(installed = it.id in installedIds) }
    }

    suspend fun getRecommendedSkills(): List<SkillSearchResult> {
        return skillRegistry.getRecommended()
    }

    suspend fun installSkill(source: String, repo: String, branch: String = "main"): Result<SkillEntity> {
        return when (source) {
            "github" -> skillInstaller.installFromGitHub(repo, branch)
            "url" -> skillInstaller.installFromUrl(repo)
            else -> Result.failure(Exception("不支持的来源: $source"))
        }
    }

    suspend fun uninstallSkill(skillId: String): Result<Unit> {
        return skillInstaller.uninstall(skillId)
    }

    suspend fun updateSkill(skillId: String): Result<SkillEntity> {
        return skillInstaller.update(skillId)
    }

    suspend fun toggleSkill(skillId: String, enabled: Boolean) {
        skillDao.setEnabled(skillId, enabled)
    }

    suspend fun getSkillConfig(skillId: String): Map<String, Any> {
        val skill = skillDao.getById(skillId) ?: return emptyMap()
        return try {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
            gson.fromJson(skill.configJson, type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun saveSkillConfig(skillId: String, config: Map<String, Any>) {
        val gson = com.google.gson.Gson()
        skillDao.updateConfig(skillId, gson.toJson(config))
    }

    fun findSkillByToolName(toolName: String): SkillEntity? {
        return getEnabledSkills().find { toolName in it.toolNames }
    }
}
