package com.example.cititor.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "book_metadata")
data class BookMetadataEntity(
    @PrimaryKey val bookId: Long,
    val charactersJson: String // Serialized List<Character>
)
