package com.example.audiobook.core.security

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class FileValidatorTest {

    private val validator = FileValidator()

    @Test
    fun `validateFileType returns PDF for valid PDF header`() {
        // %PDF (Hex: 25 50 44 46)
        val pdfHeader = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x00, 0x00) 
        val inputStream = ByteArrayInputStream(pdfHeader)

        val result = validator.validateFileType(inputStream)

        assertEquals(FileValidator.FileType.PDF, result)
    }

    @Test
    fun `validateFileType returns EPUB for valid ZIP header`() {
        // PK (Hex: 50 4B)
        val zipHeader = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        val inputStream = ByteArrayInputStream(zipHeader)

        val result = validator.validateFileType(inputStream)

        assertEquals(FileValidator.FileType.EPUB, result)
    }

    @Test
    fun `validateFileType returns UNKNOWN for invalid header`() {
        // Random bytes
        val invalidHeader = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        val inputStream = ByteArrayInputStream(invalidHeader)

        val result = validator.validateFileType(inputStream)

        assertEquals(FileValidator.FileType.UNKNOWN, result)
    }

    @Test
    fun `validateFileType returns UNKNOWN for empty stream`() {
        val emptyStream = ByteArrayInputStream(byteArrayOf())

        val result = validator.validateFileType(emptyStream)

        assertEquals(FileValidator.FileType.UNKNOWN, result)
    }
}
