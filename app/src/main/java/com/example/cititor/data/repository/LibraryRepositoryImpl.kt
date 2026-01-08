package com.example.cititor.data.repository

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import androidx.room.withTransaction
import com.example.cititor.core.database.CititorDatabase
import com.example.cititor.core.database.dao.BookDao
import com.example.cititor.core.database.entity.BookEntity
import com.example.cititor.data.worker.BookProcessingWorker
import com.example.cititor.domain.model.Book
import com.example.cititor.domain.repository.LibraryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LibraryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: CititorDatabase,
    private val dao: BookDao
) : LibraryRepository {

    private val workManager = WorkManager.getInstance(context)

    override fun getBooks(): Flow<List<Book>> {
        return dao.getAllBooks().map {
            it.map { entity -> entity.toDomainModel() }
        }
    }

    override fun getBookById(id: Long): Flow<Book?> {
        return dao.getBookByIdAsFlow(id).map { entity ->
            entity?.toDomainModel()
        }
    }

    override suspend fun insertBook(book: Book) {
        if (dao.getBookByFilePath(book.filePath) == null) {
            db.withTransaction {
                val newBookId = dao.insertBook(book.toEntity())
                startBookProcessing(newBookId, book.filePath)
            }
        }
    }

    override suspend fun deleteBook(book: Book) {
        db.withTransaction {
            // 1. Cancel background processing if any
            book.processingWorkId?.let { workId ->
                try {
                    workManager.cancelWorkById(java.util.UUID.fromString(workId))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. Delete physical cover file if it exists
            book.coverPath?.let { path ->
                try {
                    val file = java.io.File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            // 2.5. Clean up extracted book images to prevent cache pollution
            try {
                val imagesDir = java.io.File(context.cacheDir, "book_images")
                if (imagesDir.exists() && imagesDir.isDirectory) {
                    val deletedCount = imagesDir.listFiles()?.count { it.delete() } ?: 0
                    android.util.Log.d("LibraryRepository", "🧹 Cleaned up $deletedCount cached images from book_images/")
                    // Optionally delete the directory itself if empty
                    if (imagesDir.listFiles()?.isEmpty() == true) {
                        imagesDir.delete()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("LibraryRepository", "Error cleaning book_images directory", e)
            }

            // 3. Delete from DB (CASCADE will handle clean_pages)
            dao.deleteBook(book.toEntity())
        }
    }

    private suspend fun startBookProcessing(bookId: Long, bookUri: String) {
        val workRequest = OneTimeWorkRequestBuilder<BookProcessingWorker>()
            .setInputData(workDataOf(
                BookProcessingWorker.KEY_BOOK_ID to bookId,
                BookProcessingWorker.KEY_BOOK_URI to bookUri
            ))
            .build()

        workManager.enqueue(workRequest)
        // Update the book entity with the work request ID
        dao.updateWorkId(bookId, workRequest.id.toString())
    }
}

private fun BookEntity.toDomainModel(): Book {
    return Book(
        id = this.id,
        title = this.title,
        author = this.author,
        filePath = this.filePath,
        coverPath = this.coverPath,
        currentPage = this.currentPage,
        totalPages = this.totalPages,
        lastReadTimestamp = this.lastReadTimestamp,
        processingWorkId = this.processingWorkId,
        category = this.category,
        themeJson = this.themeJson
    )
}

private fun Book.toEntity(): BookEntity {
    return BookEntity(
        id = this.id,
        title = this.title,
        author = this.author,
        filePath = this.filePath,
        coverPath = this.coverPath,
        currentPage = this.currentPage,
        totalPages = this.totalPages,
        lastReadTimestamp = this.lastReadTimestamp,
        processingWorkId = this.processingWorkId,
        category = this.category,
        themeJson = this.themeJson
    )
}
