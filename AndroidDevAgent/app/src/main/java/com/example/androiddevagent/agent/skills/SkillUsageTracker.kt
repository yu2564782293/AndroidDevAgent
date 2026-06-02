package com.example.androiddevagent.agent.skills

import com.example.androiddevagent.data.SkillDao
import com.example.androiddevagent.data.SkillEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class SkillUsageRecord(
    val skillId: String,
    val toolName: String,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val executionTimeMs: Long = 0
)

data class SkillUsageStats(
    val skillId: String,
    val totalCalls: Int = 0,
    val successCalls: Int = 0,
    val failCalls: Int = 0,
    val avgExecutionTimeMs: Long = 0,
    val lastUsedAt: Long = 0,
    val toolStats: Map<String, ToolStats> = emptyMap()
)

data class ToolStats(
    val calls: Int = 0,
    val successRate: Float = 0f,
    val avgTimeMs: Long = 0
)

class SkillUsageTracker(
    private val skillDao: SkillDao
) {

    private val gson = Gson()
    private val recentRecords = mutableListOf<SkillUsageRecord>()
    private val maxRecords = 100

    fun recordUsage(record: SkillUsageRecord) {
        recentRecords.add(record)
        if (recentRecords.size > maxRecords) {
            recentRecords.removeAt(0)
        }
    }

    fun getStats(skillId: String): SkillUsageStats {
        val records = recentRecords.filter { it.skillId == skillId }
        if (records.isEmpty()) {
            return SkillUsageStats(skillId)
        }

        val totalCalls = records.size
        val successCalls = records.count { it.success }
        val failCalls = totalCalls - successCalls
        val avgTime = if (records.isNotEmpty()) records.map { it.executionTimeMs }.average().toLong() else 0
        val lastUsed = records.maxOf { it.timestamp }

        val toolStats = records.groupBy { it.toolName }.mapValues { (_, toolRecords) ->
            ToolStats(
                calls = toolRecords.size,
                successRate = if (toolRecords.isNotEmpty()) toolRecords.count { it.success }.toFloat() / toolRecords.size else 0f,
                avgTimeMs = if (toolRecords.isNotEmpty()) toolRecords.map { it.executionTimeMs }.average().toLong() else 0
            )
        }

        return SkillUsageStats(
            skillId = skillId,
            totalCalls = totalCalls,
            successCalls = successCalls,
            failCalls = failCalls,
            avgExecutionTimeMs = avgTime,
            lastUsedAt = lastUsed,
            toolStats = toolStats
        )
    }

    fun getAllStats(): Map<String, SkillUsageStats> {
        return recentRecords.groupBy { it.skillId }.mapValues { (skillId, _) ->
            getStats(skillId)
        }
    }

    fun getRecommendedByUsage(): List<String> {
        return recentRecords
            .groupBy { it.skillId }
            .entries
            .sortedByDescending { it.value.size }
            .map { it.key }
    }

    fun getLowSuccessSkills(): List<String> {
        return recentRecords
            .groupBy { it.skillId }
            .filter { (_, records) ->
                val successRate = records.count { it.success }.toFloat() / records.size
                successRate < 0.5f && records.size >= 3
            }
            .keys
            .toList()
    }

    suspend fun persistStats() {
        val allStats = getAllStats()
        for ((skillId, stats) in allStats) {
            try {
                val skill = skillDao.getById(skillId) ?: continue
                val existingConfig = try {
                    gson.fromJson<Map<String, Any>>(skill.configJson, object : TypeToken<Map<String, Any>>() {}.type) ?: emptyMap<String, Any>()
                } catch (_: Exception) {
                    emptyMap<String, Any>()
                }
                val updatedConfig = existingConfig.toMutableMap()
                updatedConfig["_usage_stats"] = gson.toJson(stats)
                skillDao.updateConfig(skillId, gson.toJson(updatedConfig))
            } catch (_: Exception) {
                // ignore
            }
        }
    }
}
