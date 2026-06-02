package com.example.androiddevagent.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TokenUsageDao {
    @Insert
    suspend fun insert(entity: TokenUsageEntity)

    @Query("SELECT * FROM token_usage ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TokenUsageEntity>>

    @Query("SELECT SUM(totalTokens) FROM token_usage WHERE timestamp >= :sinceTimestamp")
    suspend fun getTotalTokensSince(sinceTimestamp: Long): Long?

    @Query("SELECT SUM(estimatedCostUsd) FROM token_usage WHERE timestamp >= :sinceTimestamp")
    suspend fun getTotalCostSince(sinceTimestamp: Long): Double?

    @Query("SELECT SUM(totalTokens) FROM token_usage")
    suspend fun getTotalTokens(): Long?

    @Query("SELECT SUM(estimatedCostUsd) FROM token_usage")
    suspend fun getTotalCost(): Double?

    @Query("SELECT COUNT(*) FROM token_usage WHERE timestamp >= :sinceTimestamp")
    suspend fun getRequestCountSince(sinceTimestamp: Long): Int

    @Query("DELETE FROM token_usage WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOlderThan(beforeTimestamp: Long)
}
