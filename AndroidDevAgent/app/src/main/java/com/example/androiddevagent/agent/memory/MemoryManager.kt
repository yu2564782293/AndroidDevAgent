package com.example.androiddevagent.agent.memory

import com.example.androiddevagent.data.MemoryDao
import com.example.androiddevagent.data.MemoryEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryManager @Inject constructor(
    private val memoryDao: MemoryDao,
    private val smartMemoryDao: SmartMemoryDao
) {

    // ==================== 旧版记忆系统（保持兼容） ====================

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

        // 同时存入智能记忆系统
        addMemory(task, importance = 0.6f)
        for (error in errors.take(3)) {
            addMemory(error.take(500), importance = 0.8f)
        }
        if (result.isNotEmpty()) {
            addMemory(result.take(500), importance = 0.5f)
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
        try {
            // 清理低重要性且超过30天的智能记忆
            smartMemoryDao.deleteUnimportantOld(0.2f, thirtyDaysAgo)
        } catch (_: Exception) {
        }
    }

    // ==================== 智能记忆系统 ====================

    /**
     * 添加智能记忆 - 自动分类并存储
     */
    suspend fun addMemory(content: String, importance: Float = 0.5f): SmartMemoryEntity {
        val category = autoCategorize(content)
        val tags = extractTags(content)
        val embedding = serializeEmbedding(simpleEmbed(content))
        val id = "mem_${System.currentTimeMillis()}_${content.hashCode().toUInt()}"
        val now = System.currentTimeMillis()

        val memory = SmartMemoryEntity(
            id = id,
            content = content,
            category = category.name,
            tags = tags,
            embedding = embedding,
            createdAt = now,
            updatedAt = now,
            accessCount = 0,
            importance = importance
        )
        smartMemoryDao.insert(memory)
        return memory
    }

    /**
     * 手动添加记忆（指定类别）
     */
    suspend fun addMemory(content: String, category: MemoryCategory, importance: Float = 0.5f): SmartMemoryEntity {
        val tags = extractTags(content)
        val embedding = serializeEmbedding(simpleEmbed(content))
        val id = "mem_${System.currentTimeMillis()}_${content.hashCode().toUInt()}"
        val now = System.currentTimeMillis()

        val memory = SmartMemoryEntity(
            id = id,
            content = content,
            category = category.name,
            tags = tags,
            embedding = embedding,
            createdAt = now,
            updatedAt = now,
            accessCount = 0,
            importance = importance
        )
        smartMemoryDao.insert(memory)
        return memory
    }

    /**
     * 向量相似度搜索 - 返回与查询最相关的记忆
     */
    suspend fun searchMemories(query: String, topK: Int = 10): List<SmartMemoryEntity> {
        val queryEmbedding = simpleEmbed(query)
        val allMemories = smartMemoryDao.getAll()

        val scored = allMemories.mapNotNull { memory ->
            val memEmbedding = deserializeEmbedding(memory.embedding)
            if (memEmbedding.isEmpty()) return@mapNotNull null
            val similarity = cosineSimilarity(queryEmbedding, memEmbedding)
            memory to similarity
        }.sortedByDescending { it.second }
            .take(topK)

        // 更新访问计数
        scored.forEach { (memory, _) ->
            try {
                smartMemoryDao.incrementAccessCount(memory.id)
            } catch (_: Exception) {
            }
        }

        return scored.map { it.first }
    }

    /**
     * 获取与当前上下文相关的记忆
     */
    suspend fun getRelevantMemories(context: String, topK: Int = 5): List<SmartMemoryEntity> {
        return searchMemories(context, topK)
    }

    /**
     * 删除智能记忆
     */
    suspend fun deleteSmartMemory(id: String) {
        smartMemoryDao.delete(id)
    }

    /**
     * 获取所有智能记忆
     */
    suspend fun getAllSmartMemories(): List<SmartMemoryEntity> {
        return smartMemoryDao.getAll()
    }

    /**
     * 按类别获取智能记忆
     */
    suspend fun getSmartMemoriesByCategory(category: String): List<SmartMemoryEntity> {
        return smartMemoryDao.getByCategory(category)
    }

    /**
     * 文本搜索智能记忆
     */
    suspend fun searchSmartMemories(query: String): List<SmartMemoryEntity> {
        return smartMemoryDao.search(query)
    }

    /**
     * 获取最近的智能记忆
     */
    suspend fun getRecentSmartMemories(limit: Int = 20): List<SmartMemoryEntity> {
        return smartMemoryDao.getRecent(limit)
    }

    /**
     * 获取重要的智能记忆
     */
    suspend fun getImportantSmartMemories(limit: Int = 20): List<SmartMemoryEntity> {
        return smartMemoryDao.getImportant(limit)
    }

    /**
     * 构建智能记忆上下文（用于注入到系统提示中）
     */
    suspend fun buildSmartMemoryContext(currentTask: String = ""): String {
        val relevantMemories = if (currentTask.isNotEmpty()) {
            getRelevantMemories(currentTask, topK = 5)
        } else {
            getImportantSmartMemories(5)
        }
        if (relevantMemories.isEmpty()) return ""

        val sb = StringBuilder()
        sb.append("## 相关记忆\n")
        relevantMemories.forEach { memory ->
            val category = MemoryCategory.fromName(memory.category)
            sb.append("- [${category.displayName}] ${memory.content.take(200)}\n")
        }
        return sb.toString().take(2000)
    }

    // ==================== 自动分类 ====================

    /**
     * 基于关键词的自动分类
     * 未来可替换为ML模型
     */
    fun autoCategorize(content: String): MemoryCategory {
        val lower = content.lowercase()

        // 错误解决方案：包含错误、修复相关关键词
        if (ERROR_KEYWORDS.any { lower.contains(it) }) {
            return MemoryCategory.ERROR_SOLUTION
        }

        // 偏好：包含偏好、喜欢相关关键词
        if (PREFERENCE_KEYWORDS.any { lower.contains(it) }) {
            return MemoryCategory.PREFERENCE
        }

        // 指令：包含必须、应该相关关键词
        if (INSTRUCTION_KEYWORDS.any { lower.contains(it) }) {
            return MemoryCategory.INSTRUCTION
        }

        // 上下文：包含当前、正在相关关键词
        if (CONTEXT_KEYWORDS.any { lower.contains(it) }) {
            return MemoryCategory.CONTEXT
        }

        // 默认归类为事实
        return MemoryCategory.FACT
    }

    /**
     * 从内容中提取标签
     */
    private fun extractTags(content: String): List<String> {
        val tags = mutableListOf<String>()
        val lower = content.lowercase()

        // 技术标签
        TECH_TAGS.forEach { (keyword, tag) ->
            if (lower.contains(keyword)) tags.add(tag)
        }

        return tags.distinct().take(5)
    }

    // ==================== 向量嵌入 ====================

    /**
     * 简单的文本嵌入（基于哈希）
     * 将文本映射到固定维度的浮点向量
     * 未来可替换为真正的ML模型嵌入
     */
    fun simpleEmbed(text: String): FloatArray {
        val embedding = FloatArray(EMBEDDING_DIMENSION)
        if (text.isBlank()) return embedding

        // 使用字符n-gram和词级特征
        val words = text.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val charNgrams = mutableListOf<String>()

        // 提取字符3-gram
        for (word in words) {
            for (i in 0..(word.length - 3)) {
                charNgrams.add(word.substring(i, i + 3))
            }
        }

        // 词级哈希映射
        for (word in words) {
            val hash = word.hashCode()
            val idx = Math.floorMod(hash, EMBEDDING_DIMENSION)
            embedding[idx] += 1.0f
            // 二次哈希减少碰撞
            val idx2 = Math.floorMod(hash * 31 + 7, EMBEDDING_DIMENSION)
            embedding[idx2] += 0.5f
        }

        // 字符n-gram哈希映射
        for (ngram in charNgrams) {
            val hash = ngram.hashCode()
            val idx = Math.floorMod(hash, EMBEDDING_DIMENSION)
            embedding[idx] += 0.3f
        }

        // 归一化
        val norm = sqrt(embedding.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0) {
            for (i in embedding.indices) {
                embedding[i] /= norm
            }
        }

        return embedding
    }

    /**
     * 计算余弦相似度
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0) dotProduct / denominator else 0f
    }

    /**
     * 序列化嵌入向量为字符串
     */
    fun serializeEmbedding(embedding: FloatArray): String {
        return embedding.joinToString(",") { it.toString() }
    }

    /**
     * 反序列化嵌入向量
     */
    fun deserializeEmbedding(data: String): FloatArray {
        if (data.isBlank()) return FloatArray(0)
        return try {
            data.split(",").map { it.trim().toFloat() }.toFloatArray()
        } catch (_: Exception) {
            FloatArray(0)
        }
    }

    private fun sqrt(value: Float): Float = kotlin.math.sqrt(value)
    private fun sqrt(value: Double): Double = kotlin.math.sqrt(value)

    companion object {
        const val CATEGORY_USER_PREFERENCE = "user_preference"
        const val CATEGORY_PROJECT_KNOWLEDGE = "project_knowledge"
        const val CATEGORY_ERROR_EXPERIENCE = "error_experience"

        /** 嵌入向量维度 */
        const val EMBEDDING_DIMENSION = 128

        // 错误解决方案关键词
        private val ERROR_KEYWORDS = listOf(
            "error", "exception", "fail", "crash", "bug", "fix", "solved",
            "workaround", "resolved", "错误", "异常", "失败", "崩溃", "修复",
            "解决", "方案", "报错", "stacktrace", "nullpointer", "classnotfound"
        )

        // 偏好关键词
        private val PREFERENCE_KEYWORDS = listOf(
            "prefer", "like", "want", "always", "never", "favorite", "习惯",
            "喜欢", "偏好", "总是", "从不", "想要", "希望", "默认"
        )

        // 指令关键词
        private val INSTRUCTION_KEYWORDS = listOf(
            "should", "must", "need", "always do", "never do", "required",
            "必须", "需要", "应该", "不要", "务必", "禁止", "确保"
        )

        // 上下文关键词
        private val CONTEXT_KEYWORDS = listOf(
            "working on", "current", "now", "currently", "today", "this project",
            "正在", "当前", "现在", "今天", "本项目", "目前"
        )

        // 技术标签映射
        private val TECH_TAGS = mapOf(
            "kotlin" to "Kotlin",
            "java" to "Java",
            "compose" to "Compose",
            "composable" to "Compose",
            "xml" to "XML",
            "hilt" to "Hilt",
            "dagger" to "Dagger",
            "room" to "Room",
            "viewmodel" to "ViewModel",
            "livedata" to "LiveData",
            "flow" to "Flow",
            "coroutine" to "Coroutines",
            "gradle" to "Gradle",
            "retrofit" to "Retrofit",
            "okhttp" to "OkHttp",
            "navigation" to "Navigation",
            "mvvm" to "MVVM",
            "mvi" to "MVI",
            "mvp" to "MVP",
            "recyclerview" to "RecyclerView",
            "viewbinding" to "ViewBinding",
            "databinding" to "DataBinding",
            "coil" to "Coil",
            "glide" to "Glide",
            "junit" to "JUnit",
            "mockk" to "MockK",
            "espresso" to "Espresso",
            "git" to "Git",
            "github" to "GitHub",
            "ci/cd" to "CI/CD",
            "ksp" to "KSP",
            "kapt" to "KAPT"
        )
    }
}
