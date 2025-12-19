package com.example.cititor.data.text_extractor

import android.app.Application
import android.net.Uri
import com.example.cititor.domain.text_extractor.TextExtractor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

class PdfExtractor @Inject constructor(
    private val application: Application
) : TextExtractor {

    override suspend fun extractText(uri: Uri, page: Int): String = withContext(Dispatchers.IO) {
        try {
            application.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    if (page >= 0 && page < document.numberOfPages) {
                        val stripper = PDFTextStripper().apply {
                            startPage = page + 1
                            endPage = page + 1
                        }
                        stripper.getText(document)
                    } else {
                        "Page index out of bounds."
                    }
                }
            } ?: "Could not open input stream."
        } catch (e: IOException) {
            e.printStackTrace()
            "Error extracting text from PDF."
        }
    }

    override suspend fun getPageCount(uri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            application.contentResolver.openInputStream(uri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    document.numberOfPages
                }
            } ?: 0
        } catch (e: IOException) {
            e.printStackTrace()
            0
        }
    }
}
