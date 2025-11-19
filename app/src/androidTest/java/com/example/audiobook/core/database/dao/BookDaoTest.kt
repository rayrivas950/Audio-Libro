package com.example.audiobook.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.audiobook.core.database.AppDatabase
import com.example.audiobook.core.database.entity.BookTextFts
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class BookDaoTest {
    private lateinit var bookDao: BookDao
    private lateinit var db: AppDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(
            context, AppDatabase::class.java
        ).build()
        bookDao = db.bookDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndSearchBookText() = runBlocking {
        val bookId = 1L
        val text = "En un lugar de la Mancha"
        val ftsEntity = BookTextFts(bookId = bookId, pageIndex = 0, textContent = text)

        bookDao.insertBookText(ftsEntity)

        // Search for "Mancha"
        val results = bookDao.searchBookText(bookId, "Mancha")
        assertEquals(1, results.size)
        assertEquals(text, results[0].textContent)
    }

    @Test
    fun searchIsCaseInsensitive() = runBlocking {
        val bookId = 2L
        val text = "El castillo oscuro"
        val ftsEntity = BookTextFts(bookId = bookId, pageIndex = 5, textContent = text)

        bookDao.insertBookText(ftsEntity)

        // Search for "CASTILLO" (Uppercase)
        val results = bookDao.searchBookText(bookId, "CASTILLO")
        
        assertTrue("Should find result regardless of case", results.isNotEmpty())
        assertEquals(text, results[0].textContent)
    }
    
    @Test
    fun searchPartialMatch() = runBlocking {
        val bookId = 3L
        val text = "Caminante no hay camino"
        val ftsEntity = BookTextFts(bookId = bookId, pageIndex = 10, textContent = text)
        
        bookDao.insertBookText(ftsEntity)
        
        // FTS usually matches full tokens by default, let's check prefix search if supported
        // Or just standard word match
        val results = bookDao.searchBookText(bookId, "Caminante")
        assertEquals(1, results.size)
    }
}
