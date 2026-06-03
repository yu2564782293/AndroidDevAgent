package com.example.androiddevagent.agent.memory

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 记忆类别枚举
 * 代码中使用英文，UI展示使用中文
 */
enum class MemoryCategory(val displayName: String) {
    PREFERENCE("偏好"),
    FACT("事实"),
    INSTRUCTION("指令"),
    CONTEXT("上下文"),
    ERROR_SOLUTION("错误解决方案");

    companion object {
        fun fromName(name: String): MemoryCategory {
            return entries.find { it.name == name } ?: CONTEXT
        }
    }
}

/**
 * 智能记忆实体
 * 支持自动分类、向量嵌入、重要性评分
 */
@Entity(
    tableName = "smart_memories",
    indices = [
        Index(value = ["category"], name = "idx_smart_memory_category"),
        Index(value = ["importance"], name = "idx_smart_memory_importance"),
        Index(value = ["createdAt"], name = "idx_smart_memory_created"),
        Index(value = ["updatedAt"], name = "idx_smart_memory_updated")
    ]
)
data class SmartMemoryEntity(
    @PrimaryKey
    val id: String,
    val content: String,
    val category: String,
    val tags: List<String>,
    /** 序列化的浮点数组，用于向量相似度搜索 */
    val embedding: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val accessCount: Int = 0,
    /** 重要性评分 0.0 - 1.0 */
    val importance: Float = 0.5f
)
