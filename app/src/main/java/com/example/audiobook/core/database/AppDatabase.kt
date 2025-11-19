package com.example.audiobook.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.audiobook.core.database.dao.BookDao
import com.example.audiobook.core.database.entity.BookEntity
import com.example.audiobook.core.database.entity.BookmarkEntity

@Database(
    entities = [BookEntity::class, BookmarkEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
}
