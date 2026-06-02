package com.example.androiddevagent.agent.skills

import android.content.Context
import com.example.androiddevagent.data.SkillDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SkillModule {

    @Provides
    @Singleton
    fun provideSkillRuntime(skillDao: SkillDao): SkillRuntime {
        return SkillRuntime(skillDao)
    }

    @Provides
    @Singleton
    fun provideSkillInstaller(
        @ApplicationContext context: Context,
        skillDao: SkillDao
    ): SkillInstaller {
        return SkillInstaller(context, skillDao)
    }

    @Provides
    @Singleton
    fun provideSkillRegistry(skillDao: SkillDao): SkillRegistry {
        return SkillRegistry(skillDao)
    }

    @Provides
    @Singleton
    fun provideSkillManager(
        skillDao: SkillDao,
        skillRuntime: SkillRuntime,
        skillInstaller: SkillInstaller,
        skillRegistry: SkillRegistry
    ): SkillManager {
        return SkillManager(skillDao, skillRuntime, skillInstaller, skillRegistry)
    }
}
