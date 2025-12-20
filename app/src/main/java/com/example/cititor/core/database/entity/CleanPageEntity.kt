package com.example.cititor.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "clean_pages",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE // If the book is deleted, its pages are also deleted.
        )
    ],
    indices = [Index(value = ["bookId", "pageNumber"], unique = true)]
)
data class CleanPageEntity(
    @PrimaryKey(autoGenerate = true)
    val pageId: Long = 0,
    val bookId: Long,
    val pageNumber: Int,
    val content: String
)
