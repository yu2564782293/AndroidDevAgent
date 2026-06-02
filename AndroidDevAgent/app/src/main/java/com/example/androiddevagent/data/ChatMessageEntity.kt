package com.example.androiddevagent.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["sessionId"], name = "idx_chat_session"),
        Index(value = ["timestamp"], name = "idx_chat_timestamp")
    ]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val eventType: String,
    val contentJson: String,
    val timestamp: Long,
    val projectPath: String
)
