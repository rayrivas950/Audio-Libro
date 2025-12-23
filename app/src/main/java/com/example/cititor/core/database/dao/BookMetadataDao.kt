package com.example.cititor.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cititor.core.database.entity.BookMetadataEntity

@Dao
interface BookMetadataDao {
    @Query("SELECT * FROM book_metadata WHERE bookId = :bookId")
    suspend fun getMetadata(bookId: Long): BookMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: BookMetadataEntity)
}
