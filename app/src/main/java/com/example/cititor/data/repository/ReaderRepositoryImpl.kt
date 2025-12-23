package com.example.cititor.data.repository

import com.example.cititor.core.database.dao.CleanPageDao
import com.example.cititor.domain.model.TextSegment
import com.example.cititor.domain.repository.ReaderRepository
import kotlinx.serialization.json.Json
import javax.inject.Inject

class ReaderRepositoryImpl @Inject constructor(
    private val cleanPageDao: CleanPageDao,
    private val json: Json
) : ReaderRepository {

    override suspend fun getPageContent(bookId: Long, pageNumber: Int): List<TextSegment>? {
        val jsonContent = cleanPageDao.getPageContent(bookId, pageNumber)
        return if (jsonContent != null) {
            try {
                json.decodeFromString<List<TextSegment>>(jsonContent)
            } catch (e: Exception) {
                // Log the exception in a real app
                e.printStackTrace()
                null
            }
        } else {
            null
        }
    }
}
