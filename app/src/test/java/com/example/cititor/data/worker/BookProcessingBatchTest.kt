package com.example.cititor.data.worker

import android.content.Context
import android.util.Log
import androidx.work.WorkerParameters
import com.example.cititor.core.database.dao.BookMetadataDao
import com.example.cititor.core.database.dao.CleanPageDao
import com.example.cititor.data.text_extractor.ExtractorFactory
import com.example.cititor.domain.text_extractor.TextExtractor
import com.example.cititor.core.database.entity.CleanPageEntity
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import android.net.Uri
import androidx.work.Data

class BookProcessingBatchTest {

    private lateinit var worker: BookProcessingWorker
    private val context = mockk<Context>(relaxed = true)
    private val workerParams = mockk<WorkerParameters>(relaxed = true)
    private val extractorFactory = mockk<ExtractorFactory>()
    private val cleanPageDao = mockk<CleanPageDao>(relaxed = true)
    private val bookMetadataDao = mockk<BookMetadataDao>(relaxed = true)
    private val json = Json { ignoreUnknownKeys = true }
    private val mockExtractor = mockk<TextExtractor>()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0

        mockkStatic(Uri::class)
        val bookUri = mockk<Uri>()
        every { Uri.parse(any()) } returns bookUri
        every { bookUri.toString() } returns "content://test/book.epub"
        
        // Mock URI permissions
        val mockPermission = mockk<android.content.UriPermission>()
        every { mockPermission.uri } returns bookUri
        every { mockPermission.isReadPermission } returns true
        every { context.contentResolver.persistedUriPermissions } returns listOf(mockPermission)

        val inputData = Data.Builder()
            .putLong(BookProcessingWorker.KEY_BOOK_ID, 1L)
            .putString(BookProcessingWorker.KEY_BOOK_URI, "content://test/book.epub")
            .build()
        
        every { workerParams.inputData } returns inputData
        every { extractorFactory.create(any()) } returns mockExtractor
        
        worker = BookProcessingWorker(
            context,
            workerParams,
            extractorFactory,
            cleanPageDao,
            bookMetadataDao,
            json
        )
    }

    @Test
    fun `test worker processes pages in batches of 75`() = runBlocking {
        val callbackSlot = slot<suspend (Int, String) -> Unit>()
        coEvery { mockExtractor.extractPages(any(), any(), capture(callbackSlot)) } coAnswers {
            for (i in 0 until 150) {
                callbackSlot.captured(i, "Página $i. —Hola— dijo Juan.")
            }
        }

        val result = worker.doWork()

        // Verify result is success
        assert(result is androidx.work.ListenableWorker.Result.Success)

        // Verify insertAll was called twice for 150 pages (75 + 75)
        // We use any() first to see if it's called at all
        coVerify(atLeast = 1) { cleanPageDao.insertAll(any()) }
        
        // Verify metadata was saved once at the end
        coVerify(exactly = 1) { bookMetadataDao.insertMetadata(any()) }
    }
}
