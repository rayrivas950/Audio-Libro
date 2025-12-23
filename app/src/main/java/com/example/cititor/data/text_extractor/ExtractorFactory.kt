package com.example.cititor.data.text_extractor

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.cititor.domain.text_extractor.TextExtractor
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtractorFactory @Inject constructor(
    private val application: Application,
    private val pdfExtractor: PdfExtractor,
    private val epubExtractor: EpubExtractor
) {

    companion object {
        private const val TAG = "ExtractorFactory"
    }

    fun create(filePath: String): TextExtractor? {
        val uri = Uri.parse(filePath)
        val fileName = getFileName(uri)

        Log.d(TAG, "Creating extractor for file: $fileName, URI: $filePath")

        // Prioritize the file extension from the display name, as it's the most reliable source.
        if (fileName != null) {
            val lowercasedFileName = fileName.lowercase(Locale.ROOT)
            if (lowercasedFileName.endsWith(".pdf")) {
                Log.d(TAG, "Detected PDF file by extension")
                return pdfExtractor
            }
            if (lowercasedFileName.endsWith(".epub")) {
                Log.d(TAG, "Detected EPUB file by extension")
                return epubExtractor
            }
        }

        // As a fallback, check the MIME type. This can help with URIs that don't expose
        // a display name or for files without a traditional extension.
        val mimeType = application.contentResolver.getType(uri)
        Log.d(TAG, "Checking MIME type: $mimeType")
        return when (mimeType) {
            "application/pdf" -> {
                Log.d(TAG, "Detected PDF file by MIME type")
                pdfExtractor
            }
            "application/epub+zip" -> {
                Log.d(TAG, "Detected EPUB file by MIME type")
                epubExtractor
            }
            else -> {
                Log.w(TAG, "Unsupported file type. FileName: $fileName, MIME: $mimeType")
                null
            }
        }
    }

    /**
     * Queries the ContentResolver to get the display name of a file from its content URI.
     * This is the recommended way to get a file's name when using the Storage Access Framework.
     */
    private fun getFileName(uri: Uri): String? {
        if (uri.scheme == "content") {
            val cursor = application.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val displayNameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        return it.getString(displayNameIndex)
                    }
                }
            }
        }
        // For non-content URIs (like file://), fall back to the path segment.
        return uri.path?.substringAfterLast('/')
    }
}
