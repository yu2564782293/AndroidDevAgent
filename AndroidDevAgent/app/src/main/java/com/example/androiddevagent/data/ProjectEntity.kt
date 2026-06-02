package com.example.androiddevagent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val path: String,
    val name: String,
    val lastOpenedAt: Long,
    val gitBranch: String = "",
    val isActive: Boolean = false
)
