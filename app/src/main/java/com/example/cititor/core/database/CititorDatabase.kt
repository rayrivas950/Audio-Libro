package com.example.cititor.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.cititor.core.database.dao.BookDao
import com.example.cititor.core.database.entity.BookEntity

@Database(
    entities = [BookEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CititorDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

}