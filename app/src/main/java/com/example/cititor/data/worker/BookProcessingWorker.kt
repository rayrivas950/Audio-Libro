package com.example.cititor.data.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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
    private val textAnalyzer: TextAnalyzer,
    private val json: Json
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "BookProcessingWorker"
        const val KEY_BOOK_ID = "KEY_BOOK_ID"
        const val KEY_BOOK_URI = "KEY_BOOK_URI"
        const val KEY_ERROR_MSG = "KEY_ERROR_MSG"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "BookProcessingWorker started")
        
        val bookId = inputData.getLong(KEY_BOOK_ID, -1)
        val bookUriString = inputData.getString(KEY_BOOK_URI)

        if (bookId == -1L || bookUriString == null) {
            val errorMsg = "Invalid input data"
            Log.e(TAG, errorMsg)
            return@withContext Result.failure(workDataOf(KEY_ERROR_MSG to errorMsg))
        }

        val bookUri = Uri.parse(bookUriString)
        val extractor = extractorFactory.create(bookUri.toString())

        if (extractor == null) {
            val errorMsg = "Unsupported file type: $bookUriString"
            Log.e(TAG, errorMsg)
            return@withContext Result.failure(workDataOf(KEY_ERROR_MSG to errorMsg))
        }

        try {
            Log.d(TAG, "Starting streaming extraction and processing...")
            val cleanPagesBatch = mutableListOf<CleanPageEntity>()
            val batchSize = 75
            var totalProcessed = 0

            // --- Phase 1: Noise Detection (Pre-analysis) ---
            Log.d(TAG, "Phase 1: Detecting repetitive noise (headers/footers)...")
            val lineFrequency = mutableMapOf<String, Int>()
            val pageLines = mutableListOf<List<String>>()
            var totalPagesReceived = 0

            extractor.extractPages(applicationContext, bookUri) { _, rawText ->
                val lines = rawText.lines().map { it.trim() }
                pageLines.add(lines)
                
                val nonEmptyLines = lines.filter { it.isNotEmpty() }
                val candidates = mutableSetOf<String>()
                if (nonEmptyLines.size >= 1) {
                    candidates.add(nonEmptyLines.first())
                    candidates.add(nonEmptyLines.last())
                }
                if (nonEmptyLines.size >= 4) {
                    candidates.add(nonEmptyLines[1])
                    candidates.add(nonEmptyLines[nonEmptyLines.size - 2])
                }
                
                candidates.forEach { line ->
                    lineFrequency[line] = (lineFrequency[line] ?: 0) + 1
                }
                totalPagesReceived++
            }

            val noiseBlacklist = lineFrequency.filter { it.value > (totalPagesReceived * 0.3) && it.key.length > 3 }.keys
            Log.d(TAG, "Noise detection complete. Found ${noiseBlacklist.size} repetitive lines.")

            // --- Phase 2: Full Analysis & Saving ---
            Log.d(TAG, "Phase 2: Full analysis and saving to database...")
            pageLines.forEachIndexed { index, lines ->
                try {
                    val filteredLines = lines.filter { it.isEmpty() || it !in noiseBlacklist }
                    val cleanText = filteredLines.joinToString("\n")
                    
                    val segments = textAnalyzer.analyze(cleanText)
                    val jsonContent = json.encodeToString(segments)
                    
                    cleanPagesBatch.add(
                        CleanPageEntity(
                            bookId = bookId,
                            pageNumber = index,
                            content = jsonContent
                        )
                    )
                    totalProcessed++

                    if (cleanPagesBatch.size >= batchSize) {
                        cleanPageDao.insertAll(cleanPagesBatch.toList())
                        cleanPagesBatch.clear()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing page $index", e)
                }
            }

            if (cleanPagesBatch.isNotEmpty()) {
                cleanPageDao.insertAll(cleanPagesBatch)
            }
            
            Log.d(TAG, "Processing completed successfully. Total pages: $totalProcessed")
            Result.success()
        } catch (e: Exception) {
            val errorMsg = "Processing error: ${e.javaClass.simpleName} - ${e.message}"
            Log.e(TAG, errorMsg, e)
            Result.failure(workDataOf(KEY_ERROR_MSG to errorMsg))
        }
    }
}
