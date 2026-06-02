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
    fun provideSkillDependencyResolver(skillDao: SkillDao): SkillDependencyResolver {
        return SkillDependencyResolver(skillDao)
    }

    @Provides
    @Singleton
    fun provideSkillVersionManager(skillDao: SkillDao): SkillVersionManager {
        return SkillVersionManager(skillDao)
    }

    @Provides
    @Singleton
    fun provideSkillUsageTracker(skillDao: SkillDao): SkillUsageTracker {
        return SkillUsageTracker(skillDao)
    }

    @Provides
    @Singleton
    fun provideSkillPublisher(
        @ApplicationContext context: Context,
        skillDao: SkillDao
    ): SkillPublisher {
        return SkillPublisher(context, skillDao)
    }

    @Provides
    @Singleton
    fun provideSkillManager(
        skillDao: SkillDao,
        skillRuntime: SkillRuntime,
        skillInstaller: SkillInstaller,
        skillRegistry: SkillRegistry,
        skillDependencyResolver: SkillDependencyResolver,
        skillVersionManager: SkillVersionManager,
        skillUsageTracker: SkillUsageTracker,
        skillPublisher: SkillPublisher
    ): SkillManager {
        return SkillManager(
            skillDao, skillRuntime, skillInstaller, skillRegistry,
            skillDependencyResolver, skillVersionManager, skillUsageTracker, skillPublisher
        )
    }
}
