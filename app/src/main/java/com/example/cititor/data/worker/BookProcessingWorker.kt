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
            Log.d(TAG, "Extracting all pages in batch...")
            val allPagesText = extractor.extractAllPages(applicationContext, bookUri)
            val pageCount = allPagesText.size
            Log.d(TAG, "Extracted $pageCount pages")
            
            if (pageCount <= 0) {
                val errorMsg = "Could not read any pages from the document. The file might be empty, corrupted, or password-protected."
                Log.e(TAG, errorMsg)
                return@withContext Result.failure(workDataOf(KEY_ERROR_MSG to errorMsg))
            }

            val cleanPages = mutableListOf<CleanPageEntity>()

            allPagesText.forEachIndexed { i, rawText ->
                Log.d(TAG, "Processing page ${i + 1}/$pageCount")
                
                try {
                    Log.d(TAG, "Extracted ${rawText.length} characters from page $i")
                    
                    val segments = TextAnalyzer.analyze(rawText)
                    Log.d(TAG, "Analyzed into ${segments.size} segments")
                    
                    val jsonContent = json.encodeToString(segments)

                    cleanPages.add(
                        CleanPageEntity(
                            bookId = bookId,
                            pageNumber = i,
                            content = jsonContent
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing page $i", e)
                    // Continue with next page instead of failing completely
                    cleanPages.add(
                        CleanPageEntity(
                            bookId = bookId,
                            pageNumber = i,
                            content = json.encodeToString(emptyList<com.example.cititor.domain.model.TextSegment>())
                        )
                    )
                }
            }

            Log.d(TAG, "Inserting ${cleanPages.size} pages into database...")
            cleanPageDao.insertAll(cleanPages)
            Log.d(TAG, "Processing completed successfully")

            Result.success()
        } catch (e: SecurityException) {
            val errorMsg = "Permission denied accessing file: ${e.message ?: "Unknown security error"}"
            Log.e(TAG, errorMsg, e)
            Result.failure(workDataOf(KEY_ERROR_MSG to errorMsg))
        } catch (e: NullPointerException) {
            val errorMsg = "Null pointer error (possibly file stream issue): ${e.message ?: "Unknown NPE"}"
            Log.e(TAG, errorMsg, e)
            e.printStackTrace()
            Result.failure(workDataOf(KEY_ERROR_MSG to errorMsg))
        } catch (e: Exception) {
            val errorMsg = "Processing error: ${e.javaClass.simpleName} - ${e.message ?: "Unknown error"}"
            Log.e(TAG, errorMsg, e)
            e.printStackTrace()
            Result.failure(workDataOf(KEY_ERROR_MSG to errorMsg))
        }
    }
}
