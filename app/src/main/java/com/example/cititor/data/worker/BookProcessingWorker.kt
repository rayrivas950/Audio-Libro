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
    private val bookDao: com.example.cititor.core.database.dao.BookDao,
    private val characterDao: com.example.cititor.core.database.dao.CharacterDao,
    private val prosodyScriptDao: com.example.cititor.core.database.dao.ProsodyScriptDao,
    private val characterDetector: com.example.cititor.domain.analyzer.CharacterDetector,
    private val textAnalyzer: TextAnalyzer,
    private val advancedProsodyAnalyzer: com.example.cititor.domain.analyzer.AdvancedProsodyAnalyzer,
    private val timbreManager: com.example.cititor.domain.analyzer.TimbreManager,
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

        val bookEntity = bookDao.getBookById(bookId)
        val bookCategory = bookEntity?.category ?: com.example.cititor.domain.model.BookCategory.FICTION

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

            // --- Phase 1.5: Character Detection (Page by Page to avoid OOM) ---
            Log.d(TAG, "Phase 1.5: Detecting characters page by page...")
            val detectedCharactersMap = mutableMapOf<String, com.example.cititor.domain.analyzer.CharacterDetection>()
            
            pageLines.forEach { lines ->
                val pageText = lines.joinToString("\n")
                if (pageText.isNotBlank()) {
                    characterDetector.detectCharacters(pageText).forEach { detection ->
                        // Manual merge logic (same as CharacterDetector but global for the book)
                        val existing = detectedCharactersMap[detection.name]
                        if (existing == null) {
                            detectedCharactersMap[detection.name] = detection
                        } else {
                            val merged = existing.copy(
                                description = existing.description ?: detection.description,
                                genderHeuristic = existing.genderHeuristic ?: detection.genderHeuristic
                            )
                            detectedCharactersMap[detection.name] = merged
                        }
                    }
                }
            }
            
            val characterNameIdMap = mutableMapOf<String, Long>()
            detectedCharactersMap.values.forEach { detection ->
                val profile = timbreManager.generateProfile(detection)
                val characterId = characterDao.insertCharacter(
                    com.example.cititor.core.database.entity.CharacterEntity(
                        bookId = bookId,
                        name = detection.name,
                        description = detection.description,
                        gender = detection.genderHeuristic,
                        basePitch = profile.pitchShift,
                        baseSpeed = 1.0f,
                        customTimbreJson = json.encodeToString(profile)
                    )
                )
                characterNameIdMap[detection.name] = characterId
            }
            Log.d(TAG, "Found and saved ${detectedCharactersMap.size} characters.")

            // --- Phase 2: Full Analysis & Saving ---
            Log.d(TAG, "Phase 2: Full analysis and saving to database...")
            pageLines.forEachIndexed { index, lines ->
                try {
                    val filteredLines = lines.filter { it.isEmpty() || it !in noiseBlacklist }
                    val cleanText = filteredLines.joinToString("\n")
                    
                    Log.i(TAG, "[TRACE][PAGE $index] --- RAW TEXT START ---")
                    Log.i(TAG, cleanText.take(200) + "...")
                    Log.i(TAG, "[TRACE][PAGE $index] --- RAW TEXT END ---")
                    
                    val segments = textAnalyzer.analyze(cleanText)
                    
                    val prosodyScripts = advancedProsodyAnalyzer.analyzePage(cleanText, segments, bookCategory).mapIndexed { segIndex, analyzed ->
                        com.example.cititor.core.database.entity.ProsodyScriptEntity(
                            bookId = bookId,
                            pageIndex = index,
                            segmentIndex = segIndex,
                            text = analyzed.segment.text,
                            speakerId = null, // Simplified for now: always Narrator
                            arousal = analyzed.instruction.arousal,
                            valence = analyzed.instruction.valence,
                            pausePre = analyzed.instruction.pausePre,
                            pausePost = analyzed.instruction.pausePost,
                            speedMultiplier = analyzed.instruction.speedMultiplier,
                            pitchMultiplier = analyzed.instruction.pitchMultiplier
                        )
                    }
                    prosodyScriptDao.insertScripts(prosodyScripts)

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
            
            // DUMP DIAGNOSTICS FOR THE USER
            com.example.cititor.debug.DiagnosticMonitor.dumpToLogcat()
            
            Result.success()
        } catch (e: Exception) {
            val errorMsg = "Processing error: ${e.javaClass.simpleName} - ${e.message}"
            Log.e(TAG, errorMsg, e)
            Result.failure(workDataOf(KEY_ERROR_MSG to errorMsg))
        }
    }
}
