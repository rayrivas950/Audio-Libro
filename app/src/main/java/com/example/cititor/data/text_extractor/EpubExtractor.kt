package com.example.cititor.data.text_extractor

import android.content.Context
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

class EpubExtractor @Inject constructor() : TextExtractor {

    override suspend fun extractText(context: Context, uri: Uri, page: Int): String = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            tempFile = File.createTempFile("temp_epub_read", ".epub", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            val reader = Reader()
            reader.setIsIncludingTextContent(true)
            reader.setFullContent(tempFile.absolutePath)

            val bookSection: BookSection? = reader.readSection(page)
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

    override suspend fun getPageCount(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            tempFile = File.createTempFile("temp_epub_count", ".epub", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
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
