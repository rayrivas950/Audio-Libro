package com.example.cititor.domain.use_case

import com.example.cititor.domain.model.Book
import com.example.cititor.domain.repository.LibraryRepository
import javax.inject.Inject

/**
 * A use case that encapsulates the business logic for adding a new book to the library.
 */
class AddBookUseCase @Inject constructor(
    private val repository: LibraryRepository
) {

    suspend operator fun invoke(book: Book) {
        repository.insertBook(book)
    }
}
