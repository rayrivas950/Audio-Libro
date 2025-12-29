package com.example.cititor.domain.use_case

import com.example.cititor.domain.model.Book
import com.example.cititor.domain.repository.LibraryRepository
import javax.inject.Inject

/**
 * Use case to delete a book from the library.
 */
class DeleteBookUseCase @Inject constructor(
    private val repository: LibraryRepository
) {
    suspend operator fun invoke(book: Book) {
        repository.deleteBook(book)
    }
}
