package com.example.cititor.data.text_extractor

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.text.HtmlCompat
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
import javax.inject.Singleton

@Singleton
class EpubExtractor @Inject constructor() : TextExtractor {

    companion object {
        private const val TAG = "EpubExtractor"
    }

    override suspend fun extractText(context: Context, uri: Uri, page: Int): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "Extracting text from EPUB section $page, URI: $uri")
        var tempFile: File? = null
        try {
            tempFile = File.createTempFile("temp_epub_read", ".epub", context.cacheDir)
            Log.d(TAG, "Created temp file: ${tempFile.absolutePath}")
            
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                val errorMsg = "Could not open input stream for URI: $uri"
                Log.e(TAG, errorMsg)
                return@withContext errorMsg
            }
            
            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Copied EPUB to temp file successfully")

            val reader = Reader()
            reader.setIsIncludingTextContent(true)
            
            try {
                reader.setFullContent(tempFile.absolutePath)
                Log.d(TAG, "EPUB reader initialized")
            } catch (e: Exception) {
                // ParserConfigurationException es común y no fatal - el reader funciona igual
                Log.d(TAG, "EPUB reader initialized (with parser warning: ${e.javaClass.simpleName})")
            }

            val bookSection: BookSection? = try {
                reader.readSection(page)
            } catch (e: OutOfPagesException) {
                val errorMsg = "Page index out of bounds for section $page"
                Log.e(TAG, errorMsg, e)
                return@withContext errorMsg
            } catch (e: Exception) {
                val errorMsg = "Error reading section $page: ${e.javaClass.simpleName} - ${e.message}"
                Log.e(TAG, errorMsg, e)
                e.printStackTrace()
                return@withContext errorMsg
            }
            
            val content = bookSection?.sectionContent
            
            if (content != null) {
                // Convert HTML to plain text with aggressive cleaning
                val plainText = stripHtml(content)
                Log.d(TAG, "Successfully extracted ${plainText.length} characters from section $page (Stripped HTML)")
                plainText
            } else {
                val errorMsg = "Content not available for section $page"
                Log.w(TAG, errorMsg)
                errorMsg
            }

        } catch (e: ReadingException) {
            val errorMsg = "ReadingException while extracting text from EPUB: ${e.message}"
            Log.e(TAG, errorMsg, e)
            e.printStackTrace()
            "Error extracting text from EPUB: ${e.message}"
        } catch (e: IOException) {
            val errorMsg = "IOException while processing EPUB: ${e.message}"
            Log.e(TAG, errorMsg, e)
            e.printStackTrace()
            "Error processing EPUB file: ${e.message}"
        } catch (e: Exception) {
            val errorMsg = "Unexpected error while reading EPUB: ${e.javaClass.simpleName} - ${e.message}"
            Log.e(TAG, errorMsg, e)
            e.printStackTrace()
            "A general error occurred while reading the EPUB: ${e.message}"
        } finally {
            tempFile?.let {
                if (it.delete()) {
                    Log.d(TAG, "Temp file deleted successfully")
                } else {
                    Log.w(TAG, "Failed to delete temp file: ${it.absolutePath}")
                }
            }
        }
    }

    override suspend fun extractPages(
        context: Context,
        uri: Uri,
        onPageExtracted: suspend (Int, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Streaming all pages from EPUB, URI: $uri")
        var tempFile: File? = null
        
        try {
            tempFile = File.createTempFile("temp_epub_stream", ".epub", context.cacheDir)
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Could not open input stream for URI: $uri")
                return@withContext
            }
            
            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val reader = Reader()
            reader.setIsIncludingTextContent(true)
            
            try {
                reader.setFullContent(tempFile.absolutePath)
            } catch (e: Exception) {
                // ParserConfigurationException is common on Android, we ignore it
                Log.d(TAG, "EPUB reader initialized with parser warning")
            }

            // We need to find the count first, but we already have the reader open!
            var sectionIndex = 0
            while (true) {
                try {
                    val section = reader.readSection(sectionIndex)
                    val rawContent = section?.sectionContent ?: ""
                    // Apply aggressive HTML stripping here too!
                    val cleanText = stripHtml(rawContent) 
                    onPageExtracted(sectionIndex, cleanText)
                    Log.d(TAG, "Streamed EPUB section $sectionIndex (${cleanText.length} chars)")
                    sectionIndex++
                } catch (e: OutOfPagesException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error streaming EPUB section $sectionIndex", e)
                    onPageExtracted(sectionIndex, "")
                    sectionIndex++
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during EPUB streaming", e)
        } finally {
            tempFile?.delete()
        }
    }

    override suspend fun getPageCount(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        Log.d(TAG, "Getting section count for EPUB, URI: $uri")
        var tempFile: File? = null
        try {
            tempFile = File.createTempFile("temp_epub_count", ".epub", context.cacheDir)
            Log.d(TAG, "Created temp file: ${tempFile.absolutePath}")
            
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Could not open input stream for URI: $uri")
                return@withContext 0
            }
            
            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Copied EPUB to temp file successfully")

            val reader = Reader()
            reader.setFullContent(tempFile.absolutePath)
            Log.d(TAG, "EPUB reader initialized for counting")

            var sectionCount = 0
            while (true) {
                try {
                    reader.readSection(sectionCount)
                    sectionCount++
                } catch (e: OutOfPagesException) {
                    Log.d(TAG, "Reached end of EPUB, total sections: $sectionCount")
                    break
                }
            }
            Log.d(TAG, "EPUB has $sectionCount sections")
            sectionCount
        } catch (e: IOException) {
            Log.e(TAG, "IOException while getting section count: ${e.message}", e)
            e.printStackTrace()
            0
        } catch (e: ReadingException) {
            Log.e(TAG, "ReadingException while getting section count: ${e.message}", e)
            e.printStackTrace()
            0
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error getting section count: ${e.javaClass.simpleName} - ${e.message}", e)
            e.printStackTrace()
            0
        } finally {
            tempFile?.let {
                if (it.delete()) {
                    Log.d(TAG, "Temp file deleted successfully")
                } else {
                    Log.w(TAG, "Failed to delete temp file: ${it.absolutePath}")
                }
            }
        }
    }

    private fun stripHtml(html: String): String {
        // 1. Try to narrow down to body to avoid header metadata leaking in
        val bodyStart = html.indexOf("<body", ignoreCase = true)
        val contentToProcess = if (bodyStart != -1) {
            html.substring(bodyStart)
        } else {
            html
        }

        // 2. HtmlCompat handles most entities and tags
        val decoded = androidx.core.text.HtmlCompat.fromHtml(contentToProcess, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString()

        // 3. Fallback Regex for things HtmlCompat misses
        return decoded
            .replace(Regex("<[^>]*>"), "")  // Remove residual tags
            .replace(Regex("[ \\t\\xA0]+"), " ") // Normalize horizontal whitespace (spaces, tabs, nbsp) -> single space
            .replace(Regex("\\n\\s*\\n"), "\n\n") // Normalize structural newlines (max 2)
            .trim()
    }
}
