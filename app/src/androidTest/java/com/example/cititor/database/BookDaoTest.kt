package com.example.cititor.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cititor.core.database.CititorDatabase
import com.example.cititor.core.database.dao.BookDao
import com.example.cititor.core.database.entity.BookEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookDaoTest {

    private lateinit var database: CititorDatabase
    private lateinit var bookDao: BookDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CititorDatabase::class.java
        ).allowMainThreadQueries().build()
        bookDao = database.bookDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertBook_and_getAllBooks_returnsBook() = runBlocking {
        // Arrange
        val book = BookEntity(id = 1, title = "Test Book", filePath = "/test.pdf", totalPages = 100)

        // Act
        bookDao.insertBook(book)
        val books = bookDao.getAllBooks().first()

        // Assert
        assertEquals(1, books.size)
        assertEquals("Test Book", books[0].title)
    }
}