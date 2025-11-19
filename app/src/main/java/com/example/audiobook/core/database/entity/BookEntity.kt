package com.example.audiobook.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val fileUri: String, // Persisted URI string
    val totalPages: Int = 0,
    val currentPage: Int = 0,
    val lastPlayedTimestamp: Long = 0, // In milliseconds (position in audio)
    val lastInteractionTimestamp: Long = System.currentTimeMillis(), // For "Recently Opened" sort
    val coverImagePath: String? = null, // Path to local storage
    val isSearchIndexed: Boolean = false // To track if we ran FTS indexing
)
