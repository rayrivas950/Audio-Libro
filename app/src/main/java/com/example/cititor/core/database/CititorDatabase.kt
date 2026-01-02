package com.example.cititor.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.cititor.core.database.dao.BookDao
import com.example.cititor.core.database.dao.CharacterDao
import com.example.cititor.core.database.dao.CleanPageDao
import com.example.cititor.core.database.dao.ProsodyScriptDao
import com.example.cititor.core.database.entity.BookEntity
import com.example.cititor.core.database.entity.CharacterEntity
import com.example.cititor.core.database.entity.CleanPageEntity
import com.example.cititor.core.database.entity.ProsodyScriptEntity
import androidx.room.TypeConverters
import com.example.cititor.core.database.converter.RoomTypeConverters

@Database(
    entities = [
        BookEntity::class, 
        CleanPageEntity::class,
        CharacterEntity::class,
        ProsodyScriptEntity::class
    ],
    version = 9, 
    exportSchema = false
)
@TypeConverters(RoomTypeConverters::class)
abstract class CititorDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun cleanPageDao(): CleanPageDao
    abstract fun characterDao(): CharacterDao
    abstract fun prosodyScriptDao(): ProsodyScriptDao

}
