package com.example.cititor.di

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
    fun provideIntentionAnalyzer(): com.example.cititor.domain.analyzer.IntentionAnalyzer {
        return com.example.cititor.domain.analyzer.IntentionAnalyzer()
    }
}
