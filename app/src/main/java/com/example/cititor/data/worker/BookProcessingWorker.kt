package com.example.cititor.data.worker

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.cititor.core.database.dao.CleanPageDao
import com.example.cititor.core.database.entity.CleanPageEntity
import com.example.cititor.data.text_extractor.ExtractorFactory
import com.example.cititor.domain.analyzer.TextAnalyzer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@HiltWorker
class BookProcessingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val extractorFactory: ExtractorFactory,
    private val cleanPageDao: CleanPageDao,
    private val json: Json
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_BOOK_ID = "KEY_BOOK_ID"
        const val KEY_BOOK_URI = "KEY_BOOK_URI"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val bookId = inputData.getLong(KEY_BOOK_ID, -1)
        val bookUriString = inputData.getString(KEY_BOOK_URI)

        if (bookId == -1L || bookUriString == null) {
            return@withContext Result.failure()
        }

        val bookUri = Uri.parse(bookUriString)
        val extractor = extractorFactory.create(bookUri.toString())

        if (extractor == null) {
            return@withContext Result.failure()
        }

        try {
            val pageCount = extractor.getPageCount(applicationContext, bookUri)
            val cleanPages = mutableListOf<CleanPageEntity>()

            for (i in 0 until pageCount) {
                val rawText = extractor.extractText(applicationContext, bookUri, i)
                val segments = TextAnalyzer.analyze(rawText)
                val jsonContent = json.encodeToString(segments)

                cleanPages.add(
                    CleanPageEntity(
                        bookId = bookId,
                        pageNumber = i,
                        content = jsonContent
                    )
                )
            }

            // Insert all pages in a single transaction
            cleanPageDao.insertAll(cleanPages)

            Result.success()
        } catch (e: Exception) {
            // Log the exception here in a real app
            e.printStackTrace()
            Result.failure()
        }
    }
}
