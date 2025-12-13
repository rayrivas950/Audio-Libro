package com.example.cititor.domain.use_case

import com.example.cititor.domain.repository.LibraryRepository
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class GetBookUseCaseTest {

    private lateinit var getBookUseCase: GetBookUseCase
    private lateinit var mockRepository: LibraryRepository

    @Before
    fun setUp() {
        mockRepository = mockk(relaxed = true)
        getBookUseCase = GetBookUseCase(mockRepository)
    }

    @Test
    fun `invoke should call getBookById on repository`() = runTest {
        // Given
        val bookId = 123L

        // When
        getBookUseCase(bookId)

        // Then
        coVerify { mockRepository.getBookById(bookId) }
    }
}
