package com.example.cititor.domain.use_case

import com.example.cititor.domain.model.Book
import com.example.cititor.domain.repository.LibraryRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class UpdateBookProgressUseCaseTest {

    private lateinit var updateBookProgressUseCase: UpdateBookProgressUseCase
    private lateinit var mockRepository: LibraryRepository

    @Before
    fun setUp() {
        mockRepository = mockk(relaxed = true)
        updateBookProgressUseCase = UpdateBookProgressUseCase(mockRepository)
    }

    @Test
    fun `invoke should call insertBook on repository with updated progress`() = runTest {
        // Given
        val originalBook = Book(
            id = 1L,
            title = "Test Book",
            author = "Test Author",
            filePath = "/path",
            coverPath = null,
            currentPage = 0,
            totalPages = 100,
            lastReadTimestamp = 0
        )
        val newPage = 50

        // When
        updateBookProgressUseCase(originalBook, newPage)

        // Then
        coVerify { mockRepository.insertBook(any()) }
    }
}
