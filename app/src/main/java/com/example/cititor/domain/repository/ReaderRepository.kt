package com.example.cititor.domain.repository

/**
 * Defines the contract for the data layer of the reader feature.
 * This will handle operations related to a single, opened book.
 */
interface ReaderRepository {

    /**
     * Retrieves the text content of a specific page from a book.
     *
     * @param filePath The path to the book file.
     * @param pageNumber The number of the page to retrieve (0-indexed).
     * @return The text content of the page, or null if an error occurs.
     */
    suspend fun getPageContent(filePath: String, pageNumber: Int): String?

}
