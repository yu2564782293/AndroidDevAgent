package com.example.androiddevagent.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.androiddevagent.data.dao.ProjectDao
import com.example.androiddevagent.data.entity.Project

@Database(
    entities = [Project::class],
    version = 1,
    exportSchema = false
)
abstract class ProjectDatabase : RoomDatabase() {
    
    abstract fun projectDao(): ProjectDao
    
    companion object {
        @Volatile
        private var INSTANCE: ProjectDatabase? = null
        
        fun getDatabase(context: Context): ProjectDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ProjectDatabase::class.java,
                    "android_dev_agent_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}