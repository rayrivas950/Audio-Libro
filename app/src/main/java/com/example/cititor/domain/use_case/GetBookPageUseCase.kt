package com.example.cititor.domain.use_case

import com.example.cititor.domain.model.TextSegment
import com.example.cititor.domain.repository.ReaderRepository
import javax.inject.Inject

class GetBookPageUseCase @Inject constructor(
    private val repository: ReaderRepository
) {

    suspend operator fun invoke(bookId: Long, pageNumber: Int): List<TextSegment>? {
        return repository.getPageContent(bookId, pageNumber)
    }
}
