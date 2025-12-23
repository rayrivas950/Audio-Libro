package com.example.cititor.core.di

import android.content.Context
import androidx.room.Room
import com.example.cititor.core.database.CititorDatabase
import com.example.cititor.core.database.dao.BookDao
import com.example.cititor.core.database.dao.BookMetadataDao
import com.example.cititor.core.database.dao.CleanPageDao
import com.example.cititor.core.security.SecurityManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideSecurityManager(@ApplicationContext context: Context): SecurityManager {
        return SecurityManager(context)
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        securityManager: SecurityManager
    ): CititorDatabase {
        val passphrase = securityManager.getDatabasePassphrase()
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

    @Provides
    @Singleton
    fun provideCleanPageDao(database: CititorDatabase): CleanPageDao {
        return database.cleanPageDao()
    }

    @Provides
    @Singleton
    fun provideBookMetadataDao(database: CititorDatabase): BookMetadataDao {
        return database.bookMetadataDao()
    }

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            isLenient = true
            ignoreUnknownKeys = true
            // CRÍTICO: Habilitar serialización polimórfica para sealed interfaces
            classDiscriminator = "type"
        }
    }
}
