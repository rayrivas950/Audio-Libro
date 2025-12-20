package com.example.cititor.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cititor.core.database.entity.CleanPageEntity

@Dao
interface CleanPageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<CleanPageEntity>)

    @Query("SELECT content FROM clean_pages WHERE bookId = :bookId AND pageNumber = :pageNumber")
    suspend fun getPageContent(bookId: Long, pageNumber: Int): String?

}
