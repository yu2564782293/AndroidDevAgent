package com.example.androiddevagent.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "installed_skills",
    indices = [
        Index(value = ["category"], name = "idx_skill_category"),
        Index(value = ["author"], name = "idx_skill_author"),
        Index(value = ["enabled"], name = "idx_skill_enabled")
    ]
)
data class SkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val category: String,
    val icon: String,
    val tags: List<String>,
    val sourceType: String,
    val sourceUrl: String,
    val sourceBranch: String,
    val manifestJson: String,
    val toolNames: List<String>,
    val runtimeType: String,
    val runtimeEntry: String,
    val knowledge: String,
    val riskLevel: String,
    val networkAccess: Boolean,
    val fileAccess: String,
    val configJson: String,
    val enabled: Boolean = true,
    val installedAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val downloadSize: Long = 0
)
