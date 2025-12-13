package com.example.cititor.domain.use_case

import com.example.cititor.domain.model.Book
import com.example.cititor.domain.repository.LibraryRepository
import javax.inject.Inject

class UpdateBookProgressUseCase @Inject constructor(
    private val repository: LibraryRepository
) {

    suspend operator fun invoke(book: Book, currentPage: Int) {
        repository.insertBook(book.copy(currentPage = currentPage, lastReadTimestamp = System.currentTimeMillis()))
    }
}
