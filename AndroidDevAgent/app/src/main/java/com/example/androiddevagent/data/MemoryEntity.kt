package com.example.androiddevagent.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "agent_memories",
    indices = [
        Index(value = ["category"], name = "idx_memory_category"),
        Index(value = ["projectId"], name = "idx_memory_project"),
        Index(value = ["timestamp"], name = "idx_memory_timestamp")
    ],
    primaryKeys = ["category", "key", "projectId"]
)
data class MemoryEntity(
    val category: String,
    val key: String,
    val value: String,
    val projectId: String,
    val timestamp: Long = System.currentTimeMillis()
)
