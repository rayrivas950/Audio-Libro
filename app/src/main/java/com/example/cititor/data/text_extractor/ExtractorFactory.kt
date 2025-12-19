package com.example.cititor.data.text_extractor

import android.app.Application
import com.example.cititor.domain.text_extractor.TextExtractor
import javax.inject.Inject

class ExtractorFactory @Inject constructor(
    private val application: Application
) {
    fun create(filePath: String): TextExtractor? {
        return when {
            filePath.endsWith(".pdf", ignoreCase = true) -> PdfExtractor(application)
            filePath.endsWith(".epub", ignoreCase = true) -> EpubExtractor(application)
            else -> null
        }
    }
}
