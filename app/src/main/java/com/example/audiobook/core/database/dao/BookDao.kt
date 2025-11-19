package com.example.audiobook.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.audiobook.core.database.entity.BookEntity
import com.example.audiobook.core.database.entity.BookmarkEntity
import com.example.audiobook.core.database.entity.BookTextFts
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    // --- Books ---
    @Query("SELECT * FROM books ORDER BY lastInteractionTimestamp DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE title LIKE '%' || :query || '%' ORDER BY lastInteractionTimestamp DESC")
    fun searchBooksByTitle(query: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE fileUri = :uriString LIMIT 1")
    suspend fun getBookByUri(uriString: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    // --- Bookmarks ---
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY pageIndex ASC")
    fun getBookmarksForBook(bookId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    // --- Full Text Search ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookText(bookText: BookTextFts)

    @Query("""
        SELECT * FROM book_text_fts
        WHERE book_text_fts MATCH :query
        AND bookId = :bookId
    """)
    suspend fun searchBookText(bookId: Long, query: String): List<BookTextFts>
}
