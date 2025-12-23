package com.example.cititor.data.text_extractor

import android.content.Context
import android.net.Uri
import android.util.Log
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
            
            val content = bookSection?.sectionTextContent
            
            if (content != null) {
                Log.d(TAG, "Successfully extracted ${content.length} characters from section $page")
                content
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

    override suspend fun extractAllPages(context: Context, uri: Uri): List<String> = 
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Extracting all pages from EPUB in batch, URI: $uri")
            val pages = mutableListOf<String>()
            var tempFile: File? = null
            
            try {
                // 1. Create temp file ONCE
                tempFile = File.createTempFile("temp_epub_batch", ".epub", context.cacheDir)
                Log.d(TAG, "Created temp file: ${tempFile.absolutePath}")
                
                // 2. Copy EPUB ONCE
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    Log.e(TAG, "Could not open input stream for URI: $uri")
                    return@withContext emptyList()
                }
                
                inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied EPUB to temp file successfully")
                
                // 3. Initialize Reader ONCE
                val reader = Reader()
                reader.setIsIncludingTextContent(true)
                
                try {
                    reader.setFullContent(tempFile.absolutePath)
                    Log.d(TAG, "EPUB reader initialized")
                } catch (e: Exception) {
                    // ParserConfigurationException is common and non-fatal
                    Log.d(TAG, "EPUB reader initialized (with parser warning: ${e.javaClass.simpleName})")
                }
                
                // 4. Get section count
                val sectionCount = getPageCount(context, uri)
                Log.d(TAG, "EPUB has $sectionCount sections, extracting all...")
                
                // 5. Extract ALL sections in a loop
                for (i in 0 until sectionCount) {
                    try {
                        val section = reader.readSection(i)
                        val text = section?.sectionTextContent ?: ""
                        pages.add(text)
                        Log.d(TAG, "Extracted ${text.length} characters from section $i")
                    } catch (e: OutOfPagesException) {
                        Log.e(TAG, "Section $i out of bounds", e)
                        pages.add("") // Empty page for missing section
                    } catch (e: Exception) {
                        Log.e(TAG, "Error extracting section $i", e)
                        pages.add("") // Empty page on error
                    }
                }
                
                Log.d(TAG, "Successfully extracted ${pages.size} sections in batch")
                
            } catch (e: ReadingException) {
                Log.e(TAG, "ReadingException while batch extracting EPUB", e)
            } catch (e: IOException) {
                Log.e(TAG, "IOException while batch extracting EPUB", e)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error while batch extracting EPUB", e)
            } finally {
                // 6. Delete temp file ONCE at the end
                tempFile?.let {
                    if (it.delete()) {
                        Log.d(TAG, "Temp file deleted successfully")
                    } else {
                        Log.w(TAG, "Failed to delete temp file: ${it.absolutePath}")
                    }
                }
            }
            
            pages
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
}
