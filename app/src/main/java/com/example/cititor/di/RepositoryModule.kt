package com.example.cititor.di

import com.example.cititor.data.repository.LibraryRepositoryImpl
import com.example.cititor.data.repository.ReaderRepositoryImpl
import com.example.cititor.domain.repository.LibraryRepository
import com.example.cititor.domain.repository.ReaderRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindReaderRepository(impl: ReaderRepositoryImpl): ReaderRepository

}
