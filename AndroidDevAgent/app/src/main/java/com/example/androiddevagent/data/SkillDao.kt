package com.example.androiddevagent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SkillDao {

    @Query("SELECT * FROM installed_skills WHERE enabled = 1")
    suspend fun getEnabledSkills(): List<SkillEntity>

    @Query("SELECT * FROM installed_skills")
    suspend fun getAll(): List<SkillEntity>

    @Query("SELECT * FROM installed_skills WHERE id = :id")
    suspend fun getById(id: String): SkillEntity?

    @Query("SELECT * FROM installed_skills WHERE category = :category")
    suspend fun getByCategory(category: String): List<SkillEntity>

    @Query("SELECT * FROM installed_skills WHERE enabled = 1 AND :toolName IN (SELECT toolNames FROM installed_skills)")
    suspend fun findByToolName(toolName: String): SkillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(skill: SkillEntity)

    @Query("DELETE FROM installed_skills WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE installed_skills SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE installed_skills SET configJson = :configJson WHERE id = :id")
    suspend fun updateConfig(id: String, configJson: String)

    @Query("UPDATE installed_skills SET version = :version, manifestJson = :manifestJson, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateVersion(id: String, version: String, manifestJson: String, updatedAt: Long = System.currentTimeMillis())
}
