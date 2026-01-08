package com.example.cititor.core.database.entity

import androidx.room.ColumnInfo
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
    @ColumnInfo(name = "processing_work_id") val processingWorkId: String? = null, // To track the background processing job
    @ColumnInfo(name = "category") val category: com.example.cititor.domain.model.BookCategory = com.example.cititor.domain.model.BookCategory.FICTION,
    @ColumnInfo(name = "theme_json") val themeJson: String? = null
)
