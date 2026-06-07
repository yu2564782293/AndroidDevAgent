package com.example.androiddevagent.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.androiddevagent.agent.AndroidDevAgent
import com.example.androiddevagent.agent.LLMClient
import com.example.androiddevagent.agent.LLMProvider
import com.example.androiddevagent.agent.LLMProviderImpl
import com.example.androiddevagent.data.ProjectDatabase
import com.example.androiddevagent.data.dao.ProjectDao
import com.example.androiddevagent.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("llm_settings")
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideLLMClient(okHttpClient: OkHttpClient): LLMClient {
        return LLMClient(okHttpClient)
    }

    @Provides
    @Singleton
    fun provideLLMProvider(
        settingsRepository: SettingsRepository,
        llmClient: LLMClient
    ): LLMProvider {
        return LLMProviderImpl(settingsRepository, llmClient)
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
