package com.example.cititor.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.cititor.core.database.dao.BookDao
import com.example.cititor.core.database.dao.CleanPageDao
import com.example.cititor.core.database.entity.BookEntity
import com.example.cititor.core.database.entity.CleanPageEntity

@Database(
    entities = [BookEntity::class, CleanPageEntity::class],
    version = 6, // Version bumped as BookMetadataEntity is removed
    exportSchema = false
)
abstract class CititorDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun cleanPageDao(): CleanPageDao

}
