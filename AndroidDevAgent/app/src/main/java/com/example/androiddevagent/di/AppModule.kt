package com.example.androiddevagent.di

import android.content.Context
import com.example.androiddevagent.agent.AndroidDevAgent
import com.example.androiddevagent.agent.LLMProvider
import com.example.androiddevagent.agent.LLMProviderImpl
import com.example.androiddevagent.data.ProjectDatabase
import com.example.androiddevagent.data.dao.ProjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideLLMProvider(): LLMProvider {
        return LLMProviderImpl()
    }
    
    @Provides
    @Singleton
    fun provideAndroidDevAgent(llmProvider: LLMProvider): AndroidDevAgent {
        return AndroidDevAgent(llmProvider)
    }
    
    @Provides
    @Singleton
    fun provideProjectDatabase(@ApplicationContext context: Context): ProjectDatabase {
        return ProjectDatabase.getDatabase(context)
    }
    
    @Provides
    @Singleton
    fun provideProjectDao(database: ProjectDatabase): ProjectDao {
        return database.projectDao()
    }
}