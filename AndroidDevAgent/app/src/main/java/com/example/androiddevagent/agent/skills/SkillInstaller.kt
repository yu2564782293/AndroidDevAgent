package com.example.androiddevagent.agent.skills

import android.content.Context
import com.example.androiddevagent.data.SkillDao
import com.example.androiddevagent.data.SkillEntity
import java.io.File
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkillInstaller @Inject constructor(
    private val context: Context,
    private val skillDao: SkillDao
) {

    suspend fun installFromGitHub(repo: String, branch: String = "main"): Result<SkillEntity> {
        return try {
            val skillId = repo.removeSuffix(".git")
            val skillDir = File("/sdcard/DerekAI/skills", skillId.replace("/", "_"))
            skillDir.mkdirs()

            val manifestUrl = "https://raw.githubusercontent.com/$repo/$branch/skill.json"
            val manifestJson = downloadText(manifestUrl)
                ?: return Result.failure(Exception("无法下载技能清单: $manifestUrl"))

            val manifest = SkillManifestParser.parse(manifestJson)
                .getOrElse { return Result.failure(it) }

            val existingSkill = skillDao.getById(manifest.id)
            if (existingSkill != null) {
                return Result.failure(Exception("技能已安装: ${manifest.name} v${existingSkill.version}"))
            }

            val entryFile = manifest.runtime?.entry ?: "main.kts"
            val scriptUrl = "https://raw.githubusercontent.com/$repo/$branch/$entryFile"
            val scriptContent = downloadText(scriptUrl)
            if (scriptContent != null) {
                File(skillDir, entryFile).writeText(scriptContent)
            }

            val knowledgeFile = File(skillDir, "knowledge.md")
            if (!knowledgeFile.exists() && manifest.knowledge.isNotBlank()) {
                knowledgeFile.writeText(manifest.knowledge)
            }

            val entity = manifestToEntity(manifest, manifestJson, "github", "https://github.com/$repo", branch, skillDir)
            skillDao.upsert(entity)

            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(Exception("安装失败: ${e.message}"))
        }
    }

    suspend fun installFromUrl(url: String): Result<SkillEntity> {
        return try {
            val manifestJson = downloadText(url)
                ?: return Result.failure(Exception("无法下载技能清单: $url"))

            val manifest = SkillManifestParser.parse(manifestJson)
                .getOrElse { return Result.failure(it) }

            val skillDir = File("/sdcard/DerekAI/skills", manifest.id.replace("/", "_"))
            skillDir.mkdirs()

            val knowledgeFile = File(skillDir, "knowledge.md")
            if (!knowledgeFile.exists() && manifest.knowledge.isNotBlank()) {
                knowledgeFile.writeText(manifest.knowledge)
            }

            val entity = manifestToEntity(manifest, manifestJson, "url", url, "", skillDir)
            skillDao.upsert(entity)

            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(Exception("安装失败: ${e.message}"))
        }
    }

    suspend fun installFromLocal(path: String): Result<SkillEntity> {
        return try {
            val dir = File(path)
            if (!dir.exists() || !dir.isDirectory) {
                return Result.failure(Exception("目录不存在: $path"))
            }

            val manifestFile = File(dir, "skill.json")
            if (!manifestFile.exists()) {
                return Result.failure(Exception("未找到 skill.json"))
            }

            val manifestJson = manifestFile.readText()
            val manifest = SkillManifestParser.parse(manifestJson)
                .getOrElse { return Result.failure(it) }

            val skillDir = File("/sdcard/DerekAI/skills", manifest.id.replace("/", "_"))
            if (skillDir.absolutePath != dir.absolutePath) {
                dir.copyRecursively(skillDir, overwrite = true)
            }

            val entity = manifestToEntity(manifest, manifestJson, "local", path, "", skillDir)
            skillDao.upsert(entity)

            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(Exception("安装失败: ${e.message}"))
        }
    }

    suspend fun uninstall(skillId: String): Result<Unit> {
        return try {
            val skill = skillDao.getById(skillId)
                ?: return Result.failure(Exception("技能未找到: $skillId"))

            val skillDir = File("/sdcard/DerekAI/skills", skillId.replace("/", "_"))
            if (skillDir.exists()) {
                skillDir.deleteRecursively()
            }

            skillDao.delete(skillId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("卸载失败: ${e.message}"))
        }
    }

    suspend fun update(skillId: String): Result<SkillEntity> {
        val skill = skillDao.getById(skillId)
            ?: return Result.failure(Exception("技能未找到: $skillId"))

        return when (skill.sourceType) {
            "github" -> {
                skillDao.delete(skillId)
                installFromGitHub(skill.sourceUrl.removePrefix("https://github.com/"), skill.sourceBranch)
            }
            "url" -> installFromUrl(skill.sourceUrl)
            else -> Result.failure(Exception("不支持更新的来源类型: ${skill.sourceType}"))
        }
    }

    private fun manifestToEntity(
        manifest: SkillManifest,
        manifestJson: String,
        sourceType: String,
        sourceUrl: String,
        sourceBranch: String,
        skillDir: File
    ): SkillEntity {
        val configDefaults = SkillManifestParser.parseConfigDefaults(manifest.config)
        val configJson = com.google.gson.Gson().toJson(configDefaults)

        return SkillEntity(
            id = manifest.id,
            name = manifest.name,
            version = manifest.version,
            description = manifest.description,
            author = manifest.author,
            category = manifest.category,
            icon = manifest.icon,
            tags = manifest.tags,
            sourceType = sourceType,
            sourceUrl = sourceUrl,
            sourceBranch = sourceBranch,
            manifestJson = manifestJson,
            toolNames = manifest.tools.map { it.name },
            runtimeType = manifest.runtime?.type ?: "prompt",
            runtimeEntry = manifest.runtime?.entry ?: "",
            knowledge = manifest.knowledge,
            riskLevel = manifest.security?.riskLevel ?: "low",
            networkAccess = manifest.security?.networkAccess ?: false,
            fileAccess = manifest.security?.fileAccess ?: "none",
            configJson = configJson,
            enabled = true,
            downloadSize = calculateDirSize(skillDir)
        )
    }

    private fun downloadText(url: String): String? {
        return try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.getInputStream().bufferedReader().readText()
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
