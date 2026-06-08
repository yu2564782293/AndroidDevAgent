package com.example.androiddevagent.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskRecordDao {

    @Query("SELECT * FROM task_records ORDER BY createdAt DESC")
    fun getAll(): Flow<List<TaskRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(taskRecord: TaskRecordEntity)

    @Query("SELECT * FROM task_records WHERE id = :id")
    suspend fun getById(id: String): TaskRecordEntity?

    @Delete
    suspend fun delete(taskRecord: TaskRecordEntity)

    @Query("SELECT * FROM task_records ORDER BY createdAt DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<TaskRecordEntity>>
}
