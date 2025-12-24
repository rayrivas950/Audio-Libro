package com.example.cititor.domain.use_case

import com.example.cititor.core.database.dao.BookMetadataDao
import com.example.cititor.domain.model.Character
import kotlinx.serialization.json.Json
import javax.inject.Inject

class GetBookMetadataUseCase @Inject constructor(
    private val bookMetadataDao: BookMetadataDao
) {
    suspend operator fun invoke(bookId: Long): List<Character> {
        val entity = bookMetadataDao.getMetadata(bookId) ?: return emptyList()
        return try {
            Json.decodeFromString<List<Character>>(entity.charactersJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
