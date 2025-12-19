package com.example.cititor.data.text_extractor

import android.app.Application
import android.net.Uri
import com.example.cititor.domain.text_extractor.TextExtractor
import com.github.mertakdut.BookSection
import com.github.mertakdut.Reader
import com.github.mertakdut.exception.OutOfPagesException
import com.github.mertakdut.exception.ReadingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

class EpubExtractor @Inject constructor(
    private val application: Application
) : TextExtractor {

    override suspend fun extractText(uri: Uri, page: Int): String = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            tempFile = File.createTempFile("temp_epub_read", ".epub", application.cacheDir)
            application.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            val reader = Reader()
            reader.setIsIncludingTextContent(true) // Per the README, to use getSectionTextContent()
            reader.setFullContent(tempFile.absolutePath)

            val bookSection: BookSection? = reader.readSection(page)
            
            // Per the README, this is the correct way to get plain text.
            bookSection?.sectionTextContent ?: "Content not available for this section."

        } catch (e: OutOfPagesException) {
            "Page index out of bounds."
        } catch (e: ReadingException) {
            e.printStackTrace()
            "Error extracting text from EPUB."
        } catch (e: Exception) {
            e.printStackTrace()
            "A general error occurred while reading the EPUB."
        } finally {
            tempFile?.delete()
        }
    }

    override suspend fun getPageCount(uri: Uri): Int = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            tempFile = File.createTempFile("temp_epub_count", ".epub", application.cacheDir)
            application.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            val reader = Reader()
            reader.setFullContent(tempFile.absolutePath)

            var sectionCount = 0
            while (true) {
                try {
                    reader.readSection(sectionCount)
                    sectionCount++
                } catch (e: OutOfPagesException) {
                    // We have reached the end of the book. This is the exit condition.
                    break
                }
            }
            sectionCount
        } catch (e: IOException) {
            e.printStackTrace()
            0
        } catch (e: ReadingException) {
            e.printStackTrace()
            0
        } finally {
            tempFile?.delete()
        }
    }
}
