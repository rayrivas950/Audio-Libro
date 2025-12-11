package com.example.cititor.core.di

import android.content.Context
import androidx.room.Room
import com.example.cititor.core.database.CititorDatabase
import com.example.cititor.core.database.dao.BookDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): CititorDatabase {
        val passphrase = SQLiteDatabase.getBytes("ultra-secret-password".toCharArray())
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(
            context,
            CititorDatabase::class.java,
            "cititor.db"
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideBookDao(database: CititorDatabase): BookDao {
        return database.bookDao()
    }
}