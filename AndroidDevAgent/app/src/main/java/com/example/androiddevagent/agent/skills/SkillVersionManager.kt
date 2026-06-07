package com.example.androiddevagent.agent.skills

import com.example.androiddevagent.data.SkillDao
import com.example.androiddevagent.data.SkillEntity
import java.io.File

data class SkillVersion(
    val version: String,
    val manifestJson: String,
    val timestamp: Long
)

class SkillVersionManager(
    private val skillDao: SkillDao
) {

    suspend fun createBackup(skillId: String): Result<SkillVersion> {
        val skill = skillDao.getById(skillId)
            ?: return Result.failure(Exception("技能未找到: $skillId"))

        val backupDir = getBackupDir(skillId)
        backupDir.mkdirs()

        val version = SkillVersion(
            version = skill.version,
            manifestJson = skill.manifestJson,
            timestamp = System.currentTimeMillis()
        )

        val backupFile = File(backupDir, "${skill.version}_${version.timestamp}.json")
        backupFile.writeText(skill.manifestJson)

        val scriptDir = File("/sdcard/DerekAI/skills", skillId.replace("/", "_"))
        if (scriptDir.exists()) {
            val scriptBackupDir = File(backupDir, "${skill.version}_${version.timestamp}")
            scriptBackupDir.mkdirs()
            scriptDir.copyRecursively(scriptBackupDir, overwrite = true)
        }

        cleanOldBackups(skillId, maxBackups = 3)

        return Result.success(version)
    }

    suspend fun rollback(skillId: String): Result<SkillEntity> {
        val backupDir = getBackupDir(skillId)
        if (!backupDir.exists()) {
            return Result.failure(Exception("无备份可回滚: $skillId"))
        }

        val backups = backupDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?: return Result.failure(Exception("无备份文件"))

        if (backups.isEmpty()) {
            return Result.failure(Exception("无备份可回滚"))
        }

        val latestBackup = backups.first()
        val manifestJson = latestBackup.readText()
        val manifest = SkillManifestParser.parse(manifestJson)
            .getOrElse { return Result.failure(it) }

        val backupTimestamp = latestBackup.nameWithoutExtension.substringAfterLast("_").toLongOrNull() ?: 0L
        val scriptBackupDir = File(backupDir, latestBackup.nameWithoutExtension)
        val skillDir = File("/sdcard/DerekAI/skills", skillId.replace("/", "_"))

        if (scriptBackupDir.exists() && scriptBackupDir.isDirectory) {
            skillDir.deleteRecursively()
            scriptBackupDir.copyRecursively(skillDir, overwrite = true)
        }

        val currentSkill = skillDao.getById(skillId)
        if (currentSkill != null) {
            val rolledBack = currentSkill.copy(
                version = manifest.version,
                manifestJson = manifestJson,
                updatedAt = System.currentTimeMillis()
            )
            skillDao.upsert(rolledBack)
            return Result.success(rolledBack)
        }

        return Result.failure(Exception("技能未找到: $skillId"))
    }

    fun getBackupVersions(skillId: String): List<SkillVersion> {
        val backupDir = getBackupDir(skillId)
        if (!backupDir.exists()) return emptyList()

        return backupDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.map { file ->
                val parts = file.nameWithoutExtension.split("_")
                SkillVersion(
                    version = parts.firstOrNull() ?: "unknown",
                    manifestJson = file.readText(),
                    timestamp = parts.lastOrNull()?.toLongOrNull() ?: file.lastModified()
                )
            }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }

    private fun getBackupDir(skillId: String): File {
        return File("/sdcard/DerekAI/skill_backups", skillId.replace("/", "_"))
    }

    private fun cleanOldBackups(skillId: String, maxBackups: Int = 3) {
        val backupDir = getBackupDir(skillId)
        if (!backupDir.exists()) return

        val backups = backupDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.sortedByDescending { it.lastModified() }
            ?: return

        for (i in maxBackups until backups.size) {
            val backup = backups[i]
            val scriptBackupDir = File(backupDir, backup.nameWithoutExtension)
            if (scriptBackupDir.exists()) {
                scriptBackupDir.deleteRecursively()
            }
            backup.delete()
        }
    }
}
