package com.example.androiddevagent.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.androiddevagent.data.entity.Conversation
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE screenType = :screenType ORDER BY createdAt DESC")
    fun getByScreenType(screenType: String): Flow<List<Conversation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conversation: Conversation): Long

    @Delete
    suspend fun delete(conversation: Conversation)

    @Query("DELETE FROM conversations WHERE screenType = :screenType")
    suspend fun deleteByScreenType(screenType: String)

    @Query("SELECT * FROM conversations WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavorites(): Flow<List<Conversation>>

    @Query(
        """
        SELECT * FROM conversations
        WHERE userMessage LIKE '%' || :query || '%'
            OR aiResponse LIKE '%' || :query || '%'
            OR language LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
        """
    )
    fun search(query: String): Flow<List<Conversation>>
}
