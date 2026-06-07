package com.example.androiddevagent.agent.skills

import com.example.androiddevagent.data.SkillDao
import com.example.androiddevagent.data.SkillEntity

data class DependencyNode(
    val skillId: String,
    val version: String,
    val dependencies: List<String> = emptyList()
)

data class ConflictInfo(
    val toolName: String,
    val conflictingSkills: List<String>
)

data class DependencyResolution(
    val toInstall: List<String>,
    val conflicts: List<ConflictInfo>,
    val versionLocks: Map<String, String>
)

class SkillDependencyResolver(
    private val skillDao: SkillDao
) {

    suspend fun resolveDependencies(manifest: SkillManifest): DependencyResolution {
        val toInstall = mutableListOf<String>()
        val conflicts = mutableListOf<ConflictInfo>()
        val versionLocks = mutableMapOf<String, String>()

        val deps = manifest.source?.let { parseDependencies(it) } ?: emptyList()
        for (dep in deps) {
            val existing = skillDao.getById(dep)
            if (existing == null) {
                toInstall.add(dep)
            } else {
                versionLocks[dep] = existing.version
            }
        }

        val toolConflicts = detectToolConflicts(manifest)
        conflicts.addAll(toolConflicts)

        return DependencyResolution(
            toInstall = toInstall,
            conflicts = conflicts,
            versionLocks = versionLocks
        )
    }

    suspend fun detectToolConflicts(manifest: SkillManifest): List<ConflictInfo> {
        val conflicts = mutableListOf<ConflictInfo>()
        val installedSkills = skillDao.getEnabledSkills()

        for (tool in manifest.tools) {
            val existingSkill = installedSkills.find { tool.name in it.toolNames }
            if (existingSkill != null) {
                conflicts.add(ConflictInfo(
                    toolName = tool.name,
                    conflictingSkills = listOf(existingSkill.id, manifest.id)
                ))
            }
        }

        return conflicts
    }

    suspend fun checkVersionLock(skillId: String): String? {
        val skill = skillDao.getById(skillId) ?: return null
        val manifest = SkillManifestParser.parse(skill.manifestJson).getOrNull() ?: return null

        if (manifest.source?.branch != "main" && manifest.source?.branch != "master") {
            return skill.version
        }
        return null
    }

    private fun parseDependencies(source: SkillSource): List<String> {
        return emptyList()
    }
}
