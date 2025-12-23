package com.example.cititor.domain.model

/**
 * Represents a book from the domain's point of view.
 * This is a pure data class, free of any framework-specific annotations,
 * serving as the source of truth for the UI and business logic.
 */
data class Book(
    val id: Long,
    val title: String,
    val author: String?,
    val filePath: String,
    val coverPath: String?,
    val currentPage: Int,
    val totalPages: Int,
    val lastReadTimestamp: Long,
    val processingWorkId: String? // To track the background processing job
)
