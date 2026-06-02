package com.example.androiddevagent.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [TaskRecordEntity::class, ProjectEntity::class, TokenUsageEntity::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskRecordDao(): TaskRecordDao
    abstract fun projectDao(): ProjectDao
    abstract fun tokenUsageDao(): TokenUsageDao
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
            "android_dev_agent_db"
        ).fallbackToDestructiveMigrationFrom(1, 2).build()
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
}
