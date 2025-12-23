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
        processingWorkId = this.processingWorkId
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
        processingWorkId = this.processingWorkId
    )
}
