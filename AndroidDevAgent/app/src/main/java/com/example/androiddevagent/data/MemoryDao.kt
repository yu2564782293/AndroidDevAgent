package com.example.androiddevagent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MemoryDao {

    @Query("SELECT * FROM agent_memories WHERE category = :category AND projectId = :projectId")
    suspend fun getByCategory(category: String, projectId: String): List<MemoryEntity>

    @Query("SELECT * FROM agent_memories WHERE projectId = :projectId")
    suspend fun getByProject(projectId: String): List<MemoryEntity>

    @Query("SELECT * FROM agent_memories")
    suspend fun getAll(): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(memories: List<MemoryEntity>)

    @Query("DELETE FROM agent_memories WHERE category = :category AND memoryKey = :memoryKey AND projectId = :projectId")
    suspend fun delete(category: String, memoryKey: String, projectId: String)

    @Query("DELETE FROM agent_memories WHERE projectId = :projectId")
    suspend fun deleteByProject(projectId: String)

    @Query("DELETE FROM agent_memories WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
