package com.example.cititor.data.repository

import com.example.cititor.core.database.dao.BookDao
import com.example.cititor.core.database.entity.BookEntity
import com.example.cititor.domain.model.Book
import com.example.cititor.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * The concrete implementation of the LibraryRepository interface.
 * This class is responsible for orchestrating data between the local database and the domain layer.
 */
class LibraryRepositoryImpl @Inject constructor(
    private val dao: BookDao
) : LibraryRepository {

    override fun getBooks(): Flow<List<Book>> {
        return dao.getAllBooks().map {
            it.map { entity -> entity.toDomainModel() }
        }
    }

    override suspend fun insertBook(book: Book) {
        dao.insertBook(book.toEntity())
    }
}

/**
 * Converts a database [BookEntity] to a domain [Book] model.
 */
private fun BookEntity.toDomainModel(): Book {
    return Book(
        id = this.id,
        title = this.title,
        author = this.author,
        filePath = this.filePath,
        coverPath = this.coverPath,
        currentPage = this.currentPage,
        totalPages = this.totalPages,
        lastReadTimestamp = this.lastReadTimestamp
    )
}

/**
 * Converts a domain [Book] model to a database [BookEntity].
 */
private fun Book.toEntity(): BookEntity {
    return BookEntity(
        id = this.id,
        title = this.title,
        author = this.author,
        filePath = this.filePath,
        coverPath = this.coverPath,
        currentPage = this.currentPage,
        totalPages = this.totalPages,
        lastReadTimestamp = this.lastReadTimestamp
    )
}
