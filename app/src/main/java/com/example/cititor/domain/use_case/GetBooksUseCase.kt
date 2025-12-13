package com.example.cititor.domain.use_case

import com.example.cititor.domain.model.Book
import com.example.cititor.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * A use case that encapsulates the business logic for retrieving the list of all books.
 * By convention, a use case has a single public method, `invoke`.
 */
class GetBooksUseCase @Inject constructor(
    private val repository: LibraryRepository
) {

    operator fun invoke(): Flow<List<Book>> {
        return repository.getBooks()
    }
}
