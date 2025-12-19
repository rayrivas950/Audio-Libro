package com.example.cititor.data.text_extractor

import android.app.Application
import android.net.Uri
import com.example.cititor.domain.text_extractor.TextExtractor
import javax.inject.Inject

class ExtractorFactory @Inject constructor(
    private val application: Application
) {
    fun create(filePath: String): TextExtractor? {
        val uri = Uri.parse(filePath)
        val mimeType = application.contentResolver.getType(uri)

        return when (mimeType) {
            "application/pdf" -> PdfExtractor(application)
            "application/epub+zip" -> EpubExtractor(application)
            else -> null
        }
    }
}
