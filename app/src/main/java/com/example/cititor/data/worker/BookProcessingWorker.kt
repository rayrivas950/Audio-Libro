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
    private val bookMetadataDao: com.example.cititor.core.database.dao.BookMetadataDao,
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

        Log.d(TAG, "Processing book - ID: $bookId, URI: $bookUriString")

        if (bookId == -1L || bookUriString == null) {
            val errorMsg = "Invalid input data - bookId: $bookId, URI: $bookUriString"
            Log.e(TAG, errorMsg)
            return@withContext Result.failure(workDataOf(KEY_ERROR_MSG to errorMsg))
        }

        val bookUri = Uri.parse(bookUriString)
        
        // Verify URI permissions
        try {
            val persistedUris = applicationContext.contentResolver.persistedUriPermissions
            val hasPermission = persistedUris.any { it.uri == bookUri && it.isReadPermission }
            if (!hasPermission) {
                val errorMsg = "No read permission for URI: $bookUriString"
                Log.e(TAG, errorMsg)
                return@withContext Result.failure(workDataOf(KEY_ERROR_MSG to errorMsg))
            }
            Log.d(TAG, "URI permissions verified successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking URI permissions", e)
            // Continue anyway, as some URIs might not need persisted permissions
        }

        val extractor = extractorFactory.create(bookUri.toString())

        if (extractor == null) {
            val errorMsg = "Unsupported file type for URI: $bookUriString"
            Log.e(TAG, errorMsg)
            return@withContext Result.failure(workDataOf(KEY_ERROR_MSG to errorMsg))
        }

        try {
            Log.d(TAG, "Starting streaming extraction and processing...")
            
            val characterRegistry = com.example.cititor.domain.analyzer.character.CharacterRegistry()
            val cleanPagesBatch = mutableListOf<CleanPageEntity>()
            val batchSize = 75
            var totalProcessed = 0

            extractor.extractPages(applicationContext, bookUri) { index, rawText ->
                try {
                    Log.d(TAG, "Processing page ${index + 1} (Length: ${rawText.length})")
                    
                    // Analyze page using the GLOBAL character registry
                    val (segments, _) = TextAnalyzer.analyze(rawText, characterRegistry)
                    
                    val jsonContent = json.encodeToString(segments)
                    cleanPagesBatch.add(
                        CleanPageEntity(
                            bookId = bookId,
                            pageNumber = index,
                            content = jsonContent
                        )
                    )
                    totalProcessed++

                    // Batch insert to DB every 75 pages
                    if (cleanPagesBatch.size >= batchSize) {
                        Log.d(TAG, "Batch limit reached ($batchSize). Inserting into database...")
                        cleanPageDao.insertAll(cleanPagesBatch.toList())
                        cleanPagesBatch.clear()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing page $index", e)
                }
            }

            // Insert remaining pages
            if (cleanPagesBatch.isNotEmpty()) {
                Log.d(TAG, "Inserting remaining ${cleanPagesBatch.size} pages...")
                cleanPageDao.insertAll(cleanPagesBatch)
            }
            
            // Save detected characters metadata (Global for the whole book)
            val allCharacters = characterRegistry.getAll()
            if (allCharacters.isNotEmpty()) {
                Log.d(TAG, "Saving ${allCharacters.size} detected characters for the whole book...")
                val metadataEntity = com.example.cititor.core.database.entity.BookMetadataEntity(
                    bookId = bookId,
                    charactersJson = json.encodeToString(allCharacters)
                )
                bookMetadataDao.insertMetadata(metadataEntity)
            }
            
            Log.d(TAG, "Processing completed successfully. Total pages: $totalProcessed")
            Result.success()
        } catch (e: Exception) {
            val errorMsg = "Processing error: ${e.javaClass.simpleName} - ${e.message ?: "Unknown error"}"
            Log.e(TAG, errorMsg, e)
            e.printStackTrace()
            Result.failure(workDataOf(KEY_ERROR_MSG to errorMsg))
        }
    }
}
