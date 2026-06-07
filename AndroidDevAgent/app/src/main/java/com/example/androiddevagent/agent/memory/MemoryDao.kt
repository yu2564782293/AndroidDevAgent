package com.example.androiddevagent.agent.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SmartMemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: SmartMemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memories: List<SmartMemoryEntity>)

    @Query("UPDATE smart_memories SET content = :content, category = :category, tags = :tags, embedding = :embedding, updatedAt = :updatedAt, importance = :importance, accessCount = :accessCount WHERE id = :id")
    suspend fun update(id: String, content: String, category: String, tags: List<String>, embedding: String, updatedAt: Long, importance: Float, accessCount: Int)

    @Query("DELETE FROM smart_memories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM smart_memories ORDER BY updatedAt DESC")
    suspend fun getAll(): List<SmartMemoryEntity>

    @Query("SELECT * FROM smart_memories WHERE category = :category ORDER BY importance DESC, updatedAt DESC")
    suspend fun getByCategory(category: String): List<SmartMemoryEntity>

    @Query("SELECT * FROM smart_memories WHERE content LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY importance DESC, updatedAt DESC")
    suspend fun search(query: String): List<SmartMemoryEntity>

    @Query("SELECT * FROM smart_memories ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<SmartMemoryEntity>

    @Query("SELECT * FROM smart_memories ORDER BY importance DESC, accessCount DESC LIMIT :limit")
    suspend fun getImportant(limit: Int): List<SmartMemoryEntity>

    @Query("SELECT * FROM smart_memories WHERE id = :id")
    suspend fun getById(id: String): SmartMemoryEntity?

    @Query("UPDATE smart_memories SET accessCount = accessCount + 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun incrementAccessCount(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM smart_memories WHERE importance < :threshold AND createdAt < :olderThan")
    suspend fun deleteUnimportantOld(threshold: Float, olderThan: Long)
}
