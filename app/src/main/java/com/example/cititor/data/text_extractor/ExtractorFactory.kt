package com.example.cititor.data.text_extractor

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import com.example.cititor.domain.text_extractor.TextExtractor
import java.util.Locale
import javax.inject.Inject

class ExtractorFactory @Inject constructor(
    private val application: Application
) {

    fun create(filePath: String): TextExtractor? {
        val uri = Uri.parse(filePath)
        val fileName = getFileName(uri)

        // Prioritize the file extension from the display name, as it's the most reliable source.
        if (fileName != null) {
            val lowercasedFileName = fileName.lowercase(Locale.ROOT)
            if (lowercasedFileName.endsWith(".pdf")) {
                return PdfExtractor(application)
            }
            if (lowercasedFileName.endsWith(".epub")) {
                return EpubExtractor(application)
            }
        }

        // As a fallback, check the MIME type. This can help with URIs that don't expose
        // a display name or for files without a traditional extension.
        val mimeType = application.contentResolver.getType(uri)
        return when (mimeType) {
            "application/pdf" -> PdfExtractor(application)
            "application/epub+zip" -> EpubExtractor(application)
            else -> null
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
