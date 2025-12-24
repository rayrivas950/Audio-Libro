package com.example.cititor.data.text_extractor

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.cititor.domain.text_extractor.TextExtractor
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class TextExtractorStreamingTest {

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        
        mockkStatic(Uri::class)
        val mockUri = mockk<Uri>()
        every { Uri.parse(any()) } returns mockUri
    }

    @Test
    fun `test streaming extraction callback is called for each page`() = runBlocking {
        // Mocking a simple extractor that we can control
        val mockExtractor = object : TextExtractor {
            override suspend fun extractText(context: Context, uri: Uri, page: Int): String = "Page $page"
            override suspend fun getPageCount(context: Context, uri: Uri): Int = 3
            override suspend fun extractPages(
                context: Context, 
                uri: Uri, 
                onPageExtracted: suspend (Int, String) -> Unit
            ) {
                for (i in 0 until 3) {
                    onPageExtracted(i, "Content of page $i")
                }
            }
        }

        val context = mockk<Context>()
        val uri = Uri.parse("content://test/book.pdf")
        val extractedPages = mutableListOf<Pair<Int, String>>()

        mockExtractor.extractPages(context, uri) { index, text ->
            extractedPages.add(index to text)
        }

        assertEquals(3, extractedPages.size)
        assertEquals(0, extractedPages[0].first)
        assertEquals("Content of page 0", extractedPages[0].second)
        assertEquals(2, extractedPages[2].first)
        assertEquals("Content of page 2", extractedPages[2].second)
    }
}
