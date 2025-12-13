package com.example.cititor.domain.use_case

import com.example.cititor.domain.model.Book
import com.example.cititor.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetBookUseCase @Inject constructor(
    private val repository: LibraryRepository
) {

    operator fun invoke(id: Long): Flow<Book?> {
        // This will require a new method in the repository and DAO
        // For now, we are just defining the use case
        return repository.getBookById(id)
    }
}
