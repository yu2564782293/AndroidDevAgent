package com.example.androiddevagent.di

import com.example.androiddevagent.agent.llm.LlmProvider
import com.example.androiddevagent.agent.tools.ToolExecutor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideLlmProvider(): LlmProvider {
        return LlmProvider()
    }

    @Provides
    @Singleton
    fun provideToolExecutor(): ToolExecutor {
        return ToolExecutor()
    }
}
