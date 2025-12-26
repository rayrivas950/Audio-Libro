package com.example.cititor.di

import com.example.cititor.domain.analyzer.DialogueResolver
import com.example.cititor.domain.analyzer.SimpleDialogueResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    @Singleton
    fun provideDialogueResolver(): DialogueResolver {
        return SimpleDialogueResolver()
    }

    @Provides
    @Singleton
    fun provideIntentionAnalyzer(): com.example.cititor.domain.analyzer.IntentionAnalyzer {
        return com.example.cititor.domain.analyzer.IntentionAnalyzer()
    }
}
