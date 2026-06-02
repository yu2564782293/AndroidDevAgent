package com.example.androiddevagent.agent.memory

import com.example.androiddevagent.data.MemoryDao
import com.example.androiddevagent.data.MemoryEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryManager @Inject constructor(
    private val memoryDao: MemoryDao
) {

    suspend fun remember(category: String, key: String, value: String, projectId: String = "") {
        memoryDao.upsert(MemoryEntity(
            category = category,
            memoryKey = key,
            value = value,
            projectId = projectId
        ))
    }

    suspend fun recall(category: String, projectId: String = ""): Map<String, String> {
        return memoryDao.getByCategory(category, projectId)
            .associate { it.memoryKey to it.value }
    }

    suspend fun recallAll(projectId: String = ""): Map<String, Map<String, String>> {
        val memories = if (projectId.isNotEmpty()) {
            memoryDao.getByProject(projectId)
        } else {
            memoryDao.getAll()
        }
        return memories.groupBy { it.category }
            .mapValues { entries -> entries.value.associate { it.memoryKey to it.value } }
    }

    suspend fun forget(category: String, key: String, projectId: String = "") {
        memoryDao.delete(category, key, projectId)
    }

    suspend fun buildMemoryContext(projectId: String = ""): String {
        val allMemories = recallAll(projectId)
        if (allMemories.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("## Agent Memory\n")

        val userPrefs = allMemories[CATEGORY_USER_PREFERENCE]
        if (userPrefs != null && userPrefs.isNotEmpty()) {
            sb.append("### User Preferences\n")
            userPrefs.forEach { (key, value) ->
                sb.append("- $key: $value\n")
            }
        }

        val projectKnowledge = allMemories[CATEGORY_PROJECT_KNOWLEDGE]
        if (projectKnowledge != null && projectKnowledge.isNotEmpty()) {
            sb.append("### Project Knowledge\n")
            projectKnowledge.forEach { (key, value) ->
                sb.append("- $key: $value\n")
            }
        }

        val errorExperience = allMemories[CATEGORY_ERROR_EXPERIENCE]
        if (errorExperience != null && errorExperience.isNotEmpty()) {
            sb.append("### Error Experience\n")
            errorExperience.forEach { (key, value) ->
                sb.append("- $key: $value\n")
            }
        }

        return sb.toString().take(3000)
    }

    suspend fun extractMemoriesFromTask(
        task: String,
        result: String,
        projectPath: String = "",
        errors: List<String> = emptyList()
    ) {
        val projectId = projectPath.hashCode().toString()

        extractUserPreferences(task, projectId)

        if (projectPath.isNotEmpty()) {
            extractProjectKnowledge(task, result, projectId)
        }

        for (error in errors.take(3)) {
            val errorKey = error.take(50).replace(" ", "_")
            remember(CATEGORY_ERROR_EXPERIENCE, errorKey, error.take(200), projectId)
        }

        cleanupOldMemories()
    }

    private suspend fun extractUserPreferences(task: String, projectId: String) {
        val lowerTask = task.lowercase()
        if (lowerTask.contains("kotlin")) {
            remember(CATEGORY_USER_PREFERENCE, "preferred_language", "Kotlin", projectId)
        } else if (lowerTask.contains("java")) {
            remember(CATEGORY_USER_PREFERENCE, "preferred_language", "Java", projectId)
        }
        if (lowerTask.contains("compose") || lowerTask.contains("composable")) {
            remember(CATEGORY_USER_PREFERENCE, "ui_framework", "Jetpack Compose", projectId)
        } else if (lowerTask.contains("xml") && (lowerTask.contains("layout") || lowerTask.contains("view"))) {
            remember(CATEGORY_USER_PREFERENCE, "ui_framework", "XML Layout", projectId)
        }
        if (lowerTask.contains("hilt") || lowerTask.contains("dagger") || lowerTask.contains("inject")) {
            remember(CATEGORY_USER_PREFERENCE, "di_framework", "Hilt/Dagger", projectId)
        }
        if (lowerTask.contains("mvvm")) {
            remember(CATEGORY_USER_PREFERENCE, "architecture", "MVVM", projectId)
        } else if (lowerTask.contains("mvp")) {
            remember(CATEGORY_USER_PREFERENCE, "architecture", "MVP", projectId)
        }
    }

    private suspend fun extractProjectKnowledge(task: String, result: String, projectId: String) {
        val combined = "$task $result".lowercase()
        if (combined.contains("minSdk") || combined.contains("minsdk")) {
            val match = Regex("""minsdk\s*(?:=|:)?\s*(\d+)""").find(combined)
            if (match != null) {
                remember(CATEGORY_PROJECT_KNOWLEDGE, "minSdk", match.groupValues[1], projectId)
            }
        }
        if (combined.contains("targetSdk") || combined.contains("targetsdk")) {
            val match = Regex("""targetsdk\s*(?:=|:)?\s*(\d+)""").find(combined)
            if (match != null) {
                remember(CATEGORY_PROJECT_KNOWLEDGE, "targetSdk", match.groupValues[1], projectId)
            }
        }
        if (combined.contains("compileSdk") || combined.contains("compilesdk")) {
            val match = Regex("""compilesdk\s*(?:=|:)?\s*(\d+)""").find(combined)
            if (match != null) {
                remember(CATEGORY_PROJECT_KNOWLEDGE, "compileSdk", match.groupValues[1], projectId)
            }
        }
    }

    private suspend fun cleanupOldMemories() {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        try {
            memoryDao.deleteOlderThan(thirtyDaysAgo)
        } catch (_: Exception) {
        }
    }

    companion object {
        const val CATEGORY_USER_PREFERENCE = "user_preference"
        const val CATEGORY_PROJECT_KNOWLEDGE = "project_knowledge"
        const val CATEGORY_ERROR_EXPERIENCE = "error_experience"
    }
}
