package com.example.audiobook.core.database.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4
@Entity(tableName = "book_text_fts")
data class BookTextFts(
    val bookId: Long,
    val pageIndex: Int,
    val textContent: String
)
