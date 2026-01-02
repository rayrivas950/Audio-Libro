package com.example.cititor.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val author: String? = null,
    val filePath: String,
    val coverPath: String? = null,
    val currentPage: Int = 0,
    val totalPages: Int,
    val lastReadTimestamp: Long = System.currentTimeMillis(),
    val processingWorkId: String? = null, // To track the background processing job
    val category: com.example.cititor.domain.model.BookCategory = com.example.cititor.domain.model.BookCategory.FICTION
)
