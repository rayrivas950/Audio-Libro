package com.example.cititor.di

import com.example.cititor.core.tts.prosody.PatternProsodyEngine
import com.example.cititor.core.tts.prosody.ProsodyEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TtsModule {

    @Provides
    @Singleton
    fun provideProsodyEngine(): ProsodyEngine {
        return PatternProsodyEngine()
    }
}
