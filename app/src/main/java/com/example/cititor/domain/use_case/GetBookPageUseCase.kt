package com.example.cititor.domain.use_case

import com.example.cititor.domain.repository.ReaderRepository
import javax.inject.Inject

class GetBookPageUseCase @Inject constructor(
    private val repository: ReaderRepository
) {

    suspend operator fun invoke(filePath: String, pageNumber: Int): String? {
        return repository.getPageContent(filePath, pageNumber)
    }
}
