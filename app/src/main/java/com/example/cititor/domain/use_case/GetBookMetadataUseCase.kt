package com.example.cititor.domain.use_case

import com.example.cititor.core.database.dao.BookMetadataDao
import com.example.cititor.domain.model.Character
import kotlinx.serialization.json.Json
import javax.inject.Inject

class GetBookMetadataUseCase @Inject constructor(
    private val bookMetadataDao: BookMetadataDao
) {
    suspend operator fun invoke(bookId: Long): com.example.cititor.domain.model.BookMetadata {
        val entity = bookMetadataDao.getMetadata(bookId) ?: return com.example.cititor.domain.model.BookMetadata(emptyList(), emptySet())
        return try {
            val characters = Json.decodeFromString<List<Character>>(entity.charactersJson)
            val properNames = Json.decodeFromString<Set<String>>(entity.properNamesJson)
            com.example.cititor.domain.model.BookMetadata(characters, properNames)
        } catch (e: Exception) {
            com.example.cititor.domain.model.BookMetadata(emptyList(), emptySet())
        }
    }
}
