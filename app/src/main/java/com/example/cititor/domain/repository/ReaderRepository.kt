package com.example.cititor.domain.repository

import com.example.cititor.domain.model.TextSegment

/**
 * Defines the contract for the data layer of the reader feature.
 * This will handle operations related to a single, opened book.
 */
interface ReaderRepository {

    /**
     * Retrieves the structured text content of a specific page from a book's pre-processed cache.
     *
     * @param bookId The ID of the book.
     * @param pageNumber The number of the page to retrieve (0-indexed).
     * @return A list of [TextSegment] objects representing the page's content, or null if not found.
     */
    suspend fun getPageContent(bookId: Long, pageNumber: Int): List<TextSegment>?

}
