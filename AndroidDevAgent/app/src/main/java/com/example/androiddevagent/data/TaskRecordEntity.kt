package com.example.androiddevagent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_records")
data class TaskRecordEntity(
    @PrimaryKey val id: String,
    val task: String,
    val status: String,
    val filesChanged: List<String>,
    val summary: String,
    val tokenUsage: Int,
    val createdAt: Long,
    val durationMs: Long,
    val projectPath: String
)
