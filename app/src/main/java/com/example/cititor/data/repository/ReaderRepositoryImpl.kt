package com.example.cititor.data.repository

import android.app.Application
import android.net.Uri
import com.example.cititor.domain.repository.ReaderRepository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

class ReaderRepositoryImpl @Inject constructor(
    private val application: Application
) : ReaderRepository {

    override suspend fun getPageContent(filePath: String, pageNumber: Int): String? = withContext(Dispatchers.IO) {
        try {
            application.contentResolver.openInputStream(Uri.parse(filePath))?.use {
                val document = PDDocument.load(it)
                val stripper = PDFTextStripper()
                stripper.startPage = pageNumber + 1 // pdfbox is 1-indexed
                stripper.endPage = pageNumber + 1
                val text = stripper.getText(document)
                document.close()
                text
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }
}
