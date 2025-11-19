package com.example.audiobook.feature_library.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.audiobook.core.common.Resource
import com.example.audiobook.core.security.FileValidator
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream

class LibraryRepositoryImplTest {

    // Mocks
    private val context = mockk<Context>()
    private val fileValidator = mockk<FileValidator>()
    private val pdfParser = mockk<PdfParser>()
    private val contentResolver = mockk<ContentResolver>()
    private val uri = mockk<Uri>()

    // System Under Test
    private val repository = LibraryRepositoryImpl(context, fileValidator, pdfParser)

    @Test
    fun `getBookPage delegates to pdfParser`() = runTest {
        // Given
        val pageIndex = 1
        val expectedText = "This is page 1"
        coEvery { pdfParser.getPageText(uri, pageIndex) } returns Resource.Success(expectedText)

        // When
        val result = repository.getBookPage(uri, pageIndex)

        // Then
        assertTrue(result is Resource.Success)
        assertEquals(expectedText, result.data)
    }

    @Test
    fun `processNewBook returns Error if validation fails`() = runTest {
        // Given
        every { context.contentResolver } returns contentResolver
        every { contentResolver.openInputStream(uri) } returns mockk<InputStream>(relaxed = true)
        // Validator returns UNKNOWN (Invalid)
        every { fileValidator.validateFileType(any()) } returns FileValidator.FileType.UNKNOWN

        // When
        val result = repository.processNewBook(uri)

        // Then
        assertTrue(result is Resource.Error)
        assertTrue(result.message!!.contains("inválido"))
    }
}
