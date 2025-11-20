package com.example.audiobook.feature_library.domain

import android.net.Uri
import com.example.audiobook.core.common.Resource
import java.io.File

/**
 * The "Contract" for our Library.
 * The UI doesn't care HOW we get the book (local storage, cloud, download),
 * it just asks for a book and expects a result.
 */
interface LibraryRepository {
    /**
     * Takes a URI (a pointer to a file on the phone), validates it,
     * and prepares it for reading.
     */
    suspend fun processNewBook(uri: Uri): Resource<Long> // Returns the new Book ID

    fun getBooks(): kotlinx.coroutines.flow.Flow<List<com.example.audiobook.core.database.entity.BookEntity>>

    fun searchBooks(query: String): kotlinx.coroutines.flow.Flow<List<com.example.audiobook.core.database.entity.BookEntity>>

    suspend fun getBookById(id: Long): com.example.audiobook.core.database.entity.BookEntity?

    // --- In-Book Search ---
    suspend fun indexBook(bookId: Long, uri: Uri, totalPages: Int)
    suspend fun searchBookContent(bookId: Long, query: String): List<com.example.audiobook.core.database.entity.BookTextFts>

    /**
     * Extracts the text content of a specific page.
     * Used for the "Interactive Reading" mode.
     */
    suspend fun getBookPage(uri: Uri, pageIndex: Int): Resource<String>

    suspend fun getBookPageCount(uri: Uri): Int
}
