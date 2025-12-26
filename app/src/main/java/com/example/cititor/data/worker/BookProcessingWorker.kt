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
            val properNames = mutableSetOf<String>()
            val properNameRegex = Regex("""\s([A-ZÁÉÍÓÚ][a-záéíóúñ]{2,})""")
            
            val cleanPagesBatch = mutableListOf<CleanPageEntity>()
            val batchSize = 75
            var totalProcessed = 0

            // --- Phase 1: Noise Detection (Pre-analysis) ---
            Log.d(TAG, "Phase 1: Detecting repetitive noise (headers/footers)...")
            val lineFrequency = mutableMapOf<String, Int>()
            val pageLines = mutableListOf<List<String>>()
            var totalPages = 0

            extractor.extractPages(applicationContext, bookUri) { _, rawText ->
                val lines = rawText.lines().map { it.trim() }
                
                // Store lines for Phase 2
                pageLines.add(lines)
                
                // Track frequency of first 2 and last 2 NON-EMPTY lines
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
                totalPages++
            }

            val noiseBlacklist = lineFrequency.filter { it.value > (totalPages * 0.3) && it.key.length > 3 }.keys
            Log.d(TAG, "Noise detection complete. Found ${noiseBlacklist.size} repetitive lines to filter.")
            noiseBlacklist.forEach { Log.d(TAG, "Blacklisted: '$it'") }

            // --- Phase 2: Full Analysis & Saving ---
            Log.d(TAG, "Phase 2: Full analysis and saving to database...")
            pageLines.forEachIndexed { index, lines ->
                try {
                    // Filter noise lines but KEEP empty lines (paragraphs)
                    val filteredLines = lines.filter { it.isEmpty() || it !in noiseBlacklist }
                    val cleanText = filteredLines.joinToString("\n")
                    
                    Log.d(TAG, "Page $index - Raw lines: ${lines.size}, Filtered: ${filteredLines.size}")
                    if (index == 0) {
                        Log.d(TAG, "Page 0 Preview: ${cleanText.take(200).replace("\n", "\\n")}")
                    }

                    // Collect potential proper names
                    properNameRegex.findAll(cleanText).forEach { match ->
                        properNames.add(match.groupValues[1])
                    }

                    // Analyze page
                    val (segments, _) = TextAnalyzer.analyze(cleanText, characterRegistry)
                    
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

            // Insert remaining pages
            if (cleanPagesBatch.isNotEmpty()) {
                Log.d(TAG, "Inserting remaining ${cleanPagesBatch.size} pages...")
                cleanPageDao.insertAll(cleanPagesBatch)
            }
            
            // Save detected characters and proper names metadata
            val allCharacters = characterRegistry.getAll()
            // Also add character names to properNames set
            allCharacters.forEach { properNames.add(it.name) }

            Log.d(TAG, "Saving metadata for the whole book (${allCharacters.size} characters, ${properNames.size} proper names)...")
            val metadataEntity = com.example.cititor.core.database.entity.BookMetadataEntity(
                bookId = bookId,
                charactersJson = json.encodeToString(allCharacters),
                properNamesJson = json.encodeToString(properNames)
            )
            bookMetadataDao.insertMetadata(metadataEntity)
            
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
