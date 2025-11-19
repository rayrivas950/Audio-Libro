package com.example.audiobook.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE // If book is deleted, delete its bookmarks
        )
    ],
    indices = [Index(value = ["bookId"])]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val pageIndex: Int,
    val type: BookmarkType, // BOOKMARK or HIGHLIGHT
    val textSnippet: String? = null, // The text that was highlighted or context for bookmark
    val note: String? = null, // Optional user note
    val creationDate: Long = System.currentTimeMillis()
)

enum class BookmarkType {
    BOOKMARK, // Visible in "My Bookmarks"
    HIGHLIGHT // Visual yellow highlight in text
}
