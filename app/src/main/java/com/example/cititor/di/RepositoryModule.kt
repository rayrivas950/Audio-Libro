package com.example.cititor.di

import android.content.Context
import com.example.cititor.core.database.CititorDatabase
import com.example.cititor.core.database.dao.BookDao
import com.example.cititor.data.repository.LibraryRepositoryImpl
import com.example.cititor.data.repository.ReaderRepositoryImpl
import com.example.cititor.domain.repository.LibraryRepository
import com.example.cititor.domain.repository.ReaderRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindReaderRepository(impl: ReaderRepositoryImpl): ReaderRepository

    companion object {
        @Provides
        @Singleton
        fun provideLibraryRepository(
            @ApplicationContext context: Context,
            db: CititorDatabase,
            bookDao: BookDao
        ): LibraryRepository {
            return LibraryRepositoryImpl(context, db, bookDao)
        }
    }
}
