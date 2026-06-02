package com.example.androiddevagent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "token_usage")
data class TokenUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val provider: String,
    val model: String,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val estimatedCostUsd: Double,
    val timestamp: Long
)
