package com.example.cititor.core.database.converter

import androidx.room.TypeConverter
import com.example.cititor.domain.model.BookCategory

class RoomTypeConverters {
    @TypeConverter
    fun fromBookCategory(category: BookCategory): String {
        return category.name
    }

    @TypeConverter
    fun toBookCategory(name: String): BookCategory {
        return try {
            BookCategory.valueOf(name)
        } catch (e: Exception) {
            BookCategory.FICTION
        }
    }
}
