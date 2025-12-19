package com.example.cititor.data.repository

import android.net.Uri
import com.example.cititor.data.text_extractor.ExtractorFactory
import com.example.cititor.domain.repository.ReaderRepository
import javax.inject.Inject

class ReaderRepositoryImpl @Inject constructor(
    private val extractorFactory: ExtractorFactory
) : ReaderRepository {

    override suspend fun getPageContent(filePath: String, pageNumber: Int): String? {
        val extractor = extractorFactory.create(filePath)
        // The extractor itself now returns String, so we don't need the elvis operator here.
        // If the extractor is null, the function will return null, fulfilling the contract.
        return extractor?.extractText(Uri.parse(filePath), pageNumber)
    }
}
