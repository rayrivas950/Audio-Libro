package com.example.audiobook.feature_library.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.audiobook.core.common.Resource
import com.example.audiobook.core.security.FileValidator
import com.example.audiobook.feature_library.domain.LibraryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The "Worker" implementation.
 * It deals with the messy Android details like ContentResolvers and InputStreams.
 */
class LibraryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileValidator: FileValidator,
    private val pdfParser: PdfParser,
    private val bookDao: com.example.audiobook.core.database.dao.BookDao
) : LibraryRepository {

    override suspend fun processNewBook(uri: Uri): Resource<Long> {
        return withContext(Dispatchers.IO) {
            try {
                val uriString = uri.toString()
                
                // 0. Check if book already exists
                val existingBook = bookDao.getBookByUri(uriString)
                if (existingBook != null) {
                    return@withContext Resource.Success(existingBook.id)
                }

                // 1. Security Check: Validate the file content (Magic Numbers)
                val contentResolver = context.contentResolver
                
                // We open the stream just to peek at the bytes
                val isValid = contentResolver.openInputStream(uri)?.use { inputStream ->
                    val type = fileValidator.validateFileType(inputStream)
                    type != FileValidator.FileType.UNKNOWN
                } ?: false

                if (!isValid) {
                    return@withContext Resource.Error("Archivo inválido o corrupto. Solo aceptamos PDF y EPUB.")
                }

                // 2. Get Metadata (Filename)
                var fileName = "Unknown Book"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        fileName = cursor.getString(nameIndex)
                    }
                }

                // 4. Calculate total pages
            val totalPages = pdfParser.getPageCount(uri)

            // 5. Generate Cover
            val coverPath = pdfParser.generateCoverThumbnail(uri, fileName)

            // 6. Create Entity
            val newBook = com.example.audiobook.core.database.entity.BookEntity(
                title = fileName,
                fileUri = uri.toString(),
                totalPages = totalPages,
                coverImagePath = coverPath
            )

            // 7. Insert into DB
            val id = bookDao.insertBook(newBook)
            
            Resource.Success(id)

            } catch (e: Exception) {
                e.printStackTrace()
                Resource.Error("Error al procesar el archivo: ${e.localizedMessage}")
            }
        }
    }

    override fun getBooks(): kotlinx.coroutines.flow.Flow<List<com.example.audiobook.core.database.entity.BookEntity>> {
        return bookDao.getAllBooks()
    }

    override fun searchBooks(query: String): kotlinx.coroutines.flow.Flow<List<com.example.audiobook.core.database.entity.BookEntity>> {
        return bookDao.searchBooksByTitle(query)
    }

    override suspend fun getBookById(id: Long): com.example.audiobook.core.database.entity.BookEntity? {
        return bookDao.getBookById(id)
    }

    override suspend fun indexBook(bookId: Long, uri: Uri, totalPages: Int) {
        withContext(Dispatchers.IO) {
            // Loop through all pages
            for (i in 0 until totalPages) {
                // Extract text
                val result = pdfParser.getPageText(uri, i)
                if (result is Resource.Success) {
                    val text = result.data ?: continue
                    if (text.isNotBlank()) {
                        // Save to FTS table
                        val ftsEntity = com.example.audiobook.core.database.entity.BookTextFts(
                            bookId = bookId,
                            pageIndex = i,
                            textContent = text
                        )
                        bookDao.insertBookText(ftsEntity)
                    }
                }
            }
            // Mark book as indexed
            val book = bookDao.getBookById(bookId)
            book?.let {
                bookDao.updateBook(it.copy(isSearchIndexed = true))
            }
        }
    }

    override suspend fun searchBookContent(bookId: Long, query: String): List<com.example.audiobook.core.database.entity.BookTextFts> {
        return bookDao.searchBookText(bookId, query)
    }

    override suspend fun getBookPage(uri: Uri, pageIndex: Int): Resource<String> {
        // Here we delegate to the specialized Parser
        return pdfParser.getPageText(uri, pageIndex)
    }

    override suspend fun getBookPageCount(uri: Uri): Int {
        return pdfParser.getPageCount(uri)
    }
}
