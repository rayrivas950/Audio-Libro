package com.example.cititor.domain.repository

import com.example.cititor.domain.model.Book
import kotlinx.coroutines.flow.Flow

/**
 * Defines the contract for the data layer of the library feature.
 * This interface is what the domain and presentation layers will use to interact with book data,
 * without knowing the underlying implementation (Room, network, etc.).
 */
interface LibraryRepository {

    /**
     * Returns a flow that emits the list of all books in the library whenever it changes.
     */
    fun getBooks(): Flow<List<Book>>

    /**
     * Returns a flow that emits a single book, or null if it doesn't exist.
     */
    fun getBookById(id: Long): Flow<Book?>

    /**
     * Inserts a new book into the library.
     * @param book The book to be added.
     */
    suspend fun insertBook(book: Book)

    /**
     * Deletes a book and all its associated data.
     * @param book The book to be deleted.
     */
    suspend fun deleteBook(book: Book)

}
