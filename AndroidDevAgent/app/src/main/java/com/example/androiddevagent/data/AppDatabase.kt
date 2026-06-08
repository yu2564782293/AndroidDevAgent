package com.example.androiddevagent.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.androiddevagent.agent.memory.SmartMemoryEntity
import com.example.androiddevagent.agent.memory.SmartMemoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [TaskRecordEntity::class, ProjectEntity::class, TokenUsageEntity::class, ChatMessageEntity::class, MemoryEntity::class, SkillEntity::class, SmartMemoryEntity::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskRecordDao(): TaskRecordDao
    abstract fun projectDao(): ProjectDao
    abstract fun tokenUsageDao(): TokenUsageDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun skillDao(): SkillDao
    abstract fun smartMemoryDao(): SmartMemoryDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "derek_ai_db"
        ).fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6).build()
    }

    @Provides
    fun provideTaskRecordDao(database: AppDatabase): TaskRecordDao {
        return database.taskRecordDao()
    }

    @Provides
    fun provideProjectDao(database: AppDatabase): ProjectDao {
        return database.projectDao()
    }

    @Provides
    fun provideTokenUsageDao(database: AppDatabase): TokenUsageDao {
        return database.tokenUsageDao()
    }

    @Provides
    fun provideChatMessageDao(database: AppDatabase): ChatMessageDao {
        return database.chatMessageDao()
    }

    @Provides
    fun provideMemoryDao(database: AppDatabase): MemoryDao {
        return database.memoryDao()
    }

    @Provides
    fun provideSkillDao(database: AppDatabase): SkillDao {
        return database.skillDao()
    }

    @Provides
    fun provideSmartMemoryDao(database: AppDatabase): SmartMemoryDao {
        return database.smartMemoryDao()
    }
}
