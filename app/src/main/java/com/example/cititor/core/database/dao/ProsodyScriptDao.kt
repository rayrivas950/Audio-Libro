package com.example.cititor.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cititor.core.database.entity.ProsodyScriptEntity

@Dao
interface ProsodyScriptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScripts(scripts: List<ProsodyScriptEntity>)

    @Query("SELECT * FROM prosody_scripts WHERE bookId = :bookId AND pageIndex = :pageIndex ORDER BY segmentIndex ASC")
    suspend fun getScriptsForPage(bookId: Long, pageIndex: Int): List<ProsodyScriptEntity>

    @Query("DELETE FROM prosody_scripts WHERE bookId = :bookId")
    suspend fun deleteScriptsForBook(bookId: Long)
}
