package com.example.androiddevagent.data.dao

import androidx.room.*
import com.example.androiddevagent.data.entity.Project
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<Project>>
    
    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: Long): Project?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project): Long
    
    @Update
    suspend fun updateProject(project: Project)
    
    @Delete
    suspend fun deleteProject(project: Project)
    
    @Query("DELETE FROM projects")
    suspend fun deleteAllProjects()
    
    @Query("SELECT * FROM projects WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteProjects(): Flow<List<Project>>
    
    @Query("SELECT * FROM projects WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%'")
    fun searchProjects(query: String): Flow<List<Project>>
}