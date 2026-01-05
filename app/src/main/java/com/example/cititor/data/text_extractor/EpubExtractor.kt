package com.example.cititor.data.text_extractor

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Base64
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
                // Convert HTML to plain text with aggressive cleaning AND image extraction
                // We must use the tempFile here! Logic update needed:
                // Since tempFile is deleted in finally, we must ensure it exists or is passed correctly.
                // In extractText, tempFile is local var. 
                // However, the `processContentWithImages` needs the FILE to unzip from. 
                // `tempFile` variable is available in this scope? YES.
                
                val processedHtml = if (tempFile != null && tempFile.exists()) {
                    processContentWithImages(context, content, tempFile)
                } else {
                    content
                }
                
                val plainText = stripHtml(processedHtml)
                Log.d(TAG, "Successfully extracted ${plainText.length} characters from section $page (Images & Stripped HTML)")
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
                    // Apply image extraction + stripping
                    val processedHtml = if (tempFile != null && tempFile.exists()) {
                         processContentWithImages(context, rawContent, tempFile)
                    } else {
                         rawContent
                    }
                    val cleanText = stripHtml(processedHtml) 
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

        // 1.5 Pre-process structural tags to ensure newlines are preserved
        // HtmlCompat sometimes collapses blocks, so we force newlines.
        val preparedForHtmlCompat = contentToProcess
            .replace(Regex("</(p|div|h\\d)>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")

        // 2. HtmlCompat handles most entities and tags
        val decoded = androidx.core.text.HtmlCompat.fromHtml(preparedForHtmlCompat, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString()

        // 3. Fallback Regex for things HtmlCompat misses
        return decoded
            .replace(Regex("<[^>]*>"), "")  // Remove residual tags
            .replace(Regex("[ \\t\\xA0]+"), " ") // Normalize horizontal whitespace (spaces, tabs, nbsp) -> single space
            .replace(Regex("\\n\\s*\\n"), "\n\n") // Normalize structural newlines (max 2)
            .trim()
    }

    private fun processContentWithImages(context: Context, rawHtml: String, epubFile: File): String {
        // Regex to match <img ... src="path/to/image.jpg" ... />
        // Updated to be more robust with spaces and attributes
        val imgRegex = Regex("""<img\s+[^>]*src\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
        
        Log.d(TAG, "Scanning HTML chunk of size ${rawHtml.length} for images...")

        return imgRegex.replace(rawHtml) { matchResult ->
            val src = matchResult.groupValues[1]
            Log.d(TAG, "🔍 Found <IMG> tag: '${matchResult.value}'. Extracted src: '$src'")

            // CHECK FOR BASE64 (Handling malformed inputs like "../Images/data:image...")
            val savedPath = if (src.contains("data:image", ignoreCase = true)) {
                 extractBase64Image(context, src)
            } else {
                 extractImageFromZip(context, epubFile, src)
            }
            
            if (savedPath != null) {
                // Extract just the filename from the path to avoid whitespace corruption
                val filename = File(savedPath).name
                val marker = "[IMAGE_REF:$filename]"
                Log.d(TAG, "✅ Image detected and saved at: $savedPath")
                Log.d(TAG, "📝 Generating marker with FILENAME ONLY: $marker")
                // Return our special marker surrounded by newlines
                "\n\n$marker\n\n"
            } else {
                Log.w(TAG, "⚠️ Failed to extract image for src: '$src'. Tag removed.")
                "" // If image extraction fails, remove the tag
            }
        }
    }

    private fun extractBase64Image(context: Context, rawSrc: String): String? {
        try {
            // 1. Clean the src. It might be polluted with paths like "../Images/data:image..."
            val dataIndex = rawSrc.indexOf("data:image", ignoreCase = true)
            if (dataIndex == -1) return null
            
            val cleanData = rawSrc.substring(dataIndex)
            
            // 2. Parse MIME type and Data
            // Format: data:image/jpeg;base64,/9j/4AAQ...
            val semiColonIndex = cleanData.indexOf(";")
            val commaIndex = cleanData.indexOf(",")
            
            if (semiColonIndex == -1 || commaIndex == -1) {
                Log.e(TAG, "Invalid Base64 image format")
                return null
            }
            
            val mimeType = cleanData.substring(5, semiColonIndex) // e.g., image/jpeg
            val extension = if (mimeType.contains("png")) "png" else "jpg"
            val base64Data = cleanData.substring(commaIndex + 1)
            
            // 3. Decode
            val imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
            
            // 4. Save to Disk  
            // Use MD5 hash of bytes for deterministic filename
            val md5 = java.security.MessageDigest.getInstance("MD5")
            val hashBytes = md5.digest(imageBytes)
            val hashString = hashBytes.joinToString("") { "%02x".format(it) }
            val filename = "b64_${hashString.take(16)}.$extension"  // First 16 chars of MD5
            // Use CACHE DIR instead of FILES DIR - Coil can access this directly
            val imagesDir = File(context.cacheDir, "book_images")
            Log.d(TAG, "📍 Using CACHE dir: ${context.cacheDir.absolutePath}")
            Log.d(TAG, "📍 Target imagesDir: ${imagesDir.absolutePath}")
            if (!imagesDir.exists()) {
                val created = imagesDir.mkdirs()
                Log.d(TAG, "📁 Created directory: $created")
            }
            
            val outputFile = File(imagesDir, filename)
            
            if (!outputFile.exists()) {
                // Write with explicit flush and close
                FileOutputStream(outputFile).use { fos ->
                    fos.write(imageBytes)
                    fos.flush()
                    fos.fd.sync() // Force write to disk
                }
                
                // Set readable permissions for all components
                outputFile.setReadable(true, false)
                outputFile.setWritable(true, true)
                
                Log.d(TAG, "💾 Saved Base64 image to ${outputFile.name} (${imageBytes.size} bytes)")
                Log.d(TAG, "   Permissions set: readable=${outputFile.canRead()}, writable=${outputFile.canWrite()}")
            } else {
                Log.d(TAG, "⏩ Base64 image claims to exist: ${outputFile.name}")
                // Verify permissions even for existing files
                if (!outputFile.canRead()) {
                    Log.w(TAG, "   WARNING: File exists but is NOT readable! Fixing permissions...")
                    outputFile.setReadable(true, false)
                }
            }
            
            // ALWAYS VERIFY - File.exists() can lie or be cached
            val actuallyExists = outputFile.exists()
            val fileSize = if (actuallyExists) outputFile.length() else 0
            val dirContents = imagesDir.listFiles()?.joinToString { "${it.name}(${it.length()}b)" } ?: "EMPTY_OR_NULL"
            
            Log.d(TAG, "🔍 VERIFICATION:")
            Log.d(TAG, "   File.exists() = $actuallyExists")
            Log.d(TAG, "   File size = $fileSize bytes")
            Log.d(TAG, "   Path = ${outputFile.absolutePath}")
            Log.d(TAG, "   Directory contents = $dirContents")
            
            if (!actuallyExists || fileSize == 0L) {
                Log.e(TAG, "❌ CRITICAL: File does NOT actually exist or is empty!")
                Log.e(TAG, "   Expected: ${outputFile.absolutePath}")
                Log.e(TAG, "   Directory: ${imagesDir.absolutePath}")
                Log.e(TAG, "   Directory exists: ${imagesDir.exists()}")
                Log.e(TAG, "   Directory readable: ${imagesDir.canRead()}")
                Log.e(TAG, "   Directory writable: ${imagesDir.canWrite()}")
                return null
            }
            
            Log.d(TAG, "✅ CONFIRMED: File verified to exist with $fileSize bytes")
            return outputFile.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing Base64 image", e)
            return null
        }
    }

    private fun extractImageFromZip(context: Context, zipFile: File, imagePath: String): String? {
        try {
            // Clean path (sometimes relative paths like ../images/ cover this)
            val cleanName = imagePath.substringAfterLast("/")
            val imagesDir = File(context.filesDir, "book_images")
            if (!imagesDir.exists()) imagesDir.mkdirs()
            
            // Create a unique name to avoid collisions
            val uniqueName = "${zipFile.name.hashCode()}_$cleanName"
            val outputFile = File(imagesDir, uniqueName)
            
            // If already extracted, return regex
            if (outputFile.exists()) return outputFile.absolutePath

            java.util.zip.ZipFile(zipFile).use { zip ->
                val cleanNameUrlDecoded = java.net.URLDecoder.decode(cleanName, "UTF-8")
                Log.d(TAG, "🗄️ Looking in ZIP for: '$imagePath' (filename: '$cleanName', decoded: '$cleanNameUrlDecoded')")
                
                // 1. Try exact match
                var entry = zip.getEntry(imagePath)
                if (entry != null) Log.d(TAG, "   Found by exact path")
                
                // 2. Try looking for just filename in all entries (slow but robust)
                if (entry == null) {
                    val entries = zip.entries().asSequence().toList()
                    Log.d(TAG, "   Not found by path. Scanning ${entries.size} ZIP entries...")
                    // Prioritize exact filename match at end of path
                    entry = entries.find { 
                        it.name.endsWith("/$cleanName") || 
                        it.name.endsWith(cleanName) || 
                        it.name.endsWith(cleanNameUrlDecoded) 
                    }
                    if (entry != null) Log.d(TAG, "   Found by fuzzy scan: ${entry.name}")
                }

                if (entry != null) {
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outputFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    return outputFile.absolutePath
                } else {
                    Log.w(TAG, "Image entry not found in EPUB: $imagePath")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract image: $imagePath", e)
        }
        return null
    }
}
