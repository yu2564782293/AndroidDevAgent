package com.example.androiddevagent.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects ORDER BY lastOpenedAt DESC")
    fun getAll(): Flow<List<ProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("SELECT * FROM projects WHERE isActive = 1 LIMIT 1")
    fun getActive(): Flow<ProjectEntity?>

    @Transaction
    suspend fun setActive(path: String) {
        deactivateAll()
        activateProject(path, System.currentTimeMillis())
    }

    @Query("UPDATE projects SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE projects SET isActive = 1, lastOpenedAt = :timestamp WHERE path = :path")
    suspend fun activateProject(path: String, timestamp: Long)
}
