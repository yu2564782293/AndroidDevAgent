package com.example.androiddevagent.agent.skills

import com.example.androiddevagent.agent.llm.ChatCompletionRequest
import com.example.androiddevagent.agent.tools.ToolResult
import com.example.androiddevagent.data.SkillDao
import com.example.androiddevagent.data.SkillEntity

class SkillManager(
    private val skillDao: SkillDao,
    private val skillRuntime: SkillRuntime,
    private val skillInstaller: SkillInstaller,
    private val skillRegistry: SkillRegistry,
    private val dependencyResolver: SkillDependencyResolver,
    private val versionManager: SkillVersionManager,
    private val usageTracker: SkillUsageTracker,
    private val skillPublisher: SkillPublisher
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

        val startTime = System.currentTimeMillis()
        val result = skillRuntime.executeTool(skill.id, toolName, args, projectPath)
        val executionTime = System.currentTimeMillis() - startTime

        usageTracker.recordUsage(SkillUsageRecord(
            skillId = skill.id,
            toolName = toolName,
            success = result.success,
            executionTimeMs = executionTime
        ))

        return result
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
        val result = when (source) {
            "github" -> skillInstaller.installFromGitHub(repo, branch)
            "url" -> skillInstaller.installFromUrl(repo)
            else -> Result.failure(Exception("不支持的来源: $source"))
        }
        return result
    }

    suspend fun checkDependencies(manifest: SkillManifest): DependencyResolution {
        return dependencyResolver.resolveDependencies(manifest)
    }

    suspend fun uninstallSkill(skillId: String): Result<Unit> {
        return skillInstaller.uninstall(skillId)
    }

    suspend fun updateSkill(skillId: String): Result<SkillEntity> {
        versionManager.createBackup(skillId)
        return skillInstaller.update(skillId)
    }

    suspend fun rollbackSkill(skillId: String): Result<SkillEntity> {
        return versionManager.rollback(skillId)
    }

    fun getBackupVersions(skillId: String): List<SkillVersion> {
        return versionManager.getBackupVersions(skillId)
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

    fun getUsageStats(skillId: String): SkillUsageStats {
        return usageTracker.getStats(skillId)
    }

    fun getAllUsageStats(): Map<String, SkillUsageStats> {
        return usageTracker.getAllStats()
    }

    suspend fun createSkillFromTemplate(
        type: String,
        id: String,
        name: String,
        description: String,
        toolName: String,
        toolDescription: String,
        knowledge: String,
        author: String
    ): Result<SkillEntity> {
        val manifestJson = when (type) {
            "script" -> SkillTemplateGenerator.generateScriptSkill(id, name, description, toolName, toolDescription, author)
            "prompt" -> SkillTemplateGenerator.generatePromptSkill(id, name, description, knowledge, author)
            "hybrid" -> SkillTemplateGenerator.generateHybridSkill(id, name, description, toolName, toolDescription, knowledge, author)
            else -> return Result.failure(Exception("不支持的技能类型: $type"))
        }

        val manifest = SkillManifestParser.parse(manifestJson)
            .getOrElse { return Result.failure(it) }

        val skillDir = java.io.File("/sdcard/DerekAI/skills", id.replace("/", "_"))
        skillDir.mkdirs()
        java.io.File(skillDir, "skill.json").writeText(manifestJson)

        if (type == "script" || type == "hybrid") {
            val scriptContent = SkillTemplateGenerator.generateScriptTemplate(toolName)
            java.io.File(skillDir, "main.kts").writeText(scriptContent)
        }

        return skillInstaller.installFromLocal(skillDir.absolutePath)
    }

    suspend fun publishSkill(skillId: String): PublishResult {
        return skillPublisher.publishToMarketplace(skillId)
    }

    suspend fun exportSkillPackage(skillId: String, outputDir: String = "/sdcard/DerekAI/exports"): PublishResult {
        return skillPublisher.exportSkillPackage(skillId, outputDir)
    }

    fun validateScriptSafety(skillId: String, script: String): List<String> {
        val skill = getEnabledSkills().find { it.id == skillId } ?: return listOf("技能未找到")
        val constraints = SkillSandbox.buildConstraints(skill, "")
        return SkillSandbox.validateScriptSafety(script, constraints)
    }
}
