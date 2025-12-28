package com.example.cititor.domain.sanitizer

import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cititor.core.database.CititorDatabase
import com.example.cititor.data.text_extractor.PdfExtractor
import com.example.cititor.domain.analyzer.*
import com.example.cititor.domain.dictionary.DictionaryManager
import com.example.cititor.domain.sanitizer.TextSanitizer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * End-to-End test that simulates the BookProcessingWorker flow.
 */
class WorkFlowIntegrationTest {

    private lateinit var context: Context
    private lateinit var dictionaryManager: DictionaryManager
    private lateinit var textSanitizer: TextSanitizer
    private lateinit var consistencyAuditor: ConsistencyAuditor
    private lateinit var textAnalyzer: TextAnalyzer
    private lateinit var pdfExtractor: PdfExtractor

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        dictionaryManager = DictionaryManager(context)
        textSanitizer = TextSanitizer(dictionaryManager)
        consistencyAuditor = ConsistencyAuditor(dictionaryManager)
        
        // Manual DI for testing
        textAnalyzer = TextAnalyzer(
            textSanitizer = textSanitizer,
            dialogueResolver = SimpleDialogueResolver(),
            intentionAnalyzer = IntentionAnalyzer(),
            consistencyAuditor = consistencyAuditor
        )
        
        pdfExtractor = PdfExtractor()
    }

    @After
    fun teardown() {
        // Nothing to clean
    }

    @Test
    fun testFullProcessingFlow() {
        runBlocking {
            Log.i("FLOW_TEST", "--- STARTING FULL FLOW TEST ---")
        
        val assetName = "torture_test.pdf"
        val testFile = File(context.cacheDir, "flow_test.pdf")
        val instrumentationContext = InstrumentationRegistry.getInstrumentation().context
        instrumentationContext.assets.open(assetName).use { input ->
            FileOutputStream(testFile).use { output ->
                input.copyTo(output)
            }
        }
        val uri = android.net.Uri.fromFile(testFile)

        // 2. Simulate Worker Phase 1 (Noise detection - skip for simplicity or include if suspected)
        
        // 3. Simulate Worker Phase 2 (Extraction & Analysis)
        var totalPages = 0
        pdfExtractor.extractPages(context, uri) { pageIndex, rawText ->
            Log.i("FLOW_TEST", "[STEP 1: RAW] Page $pageIndex extracted, length: ${rawText.length}")
            
            // Analyze (Sanitization + Audit + Segmentation)
            val segments = textAnalyzer.analyze(rawText, pageIndex = pageIndex)
            Log.i("FLOW_TEST", "[STEP 2: ANALYZED] Page $pageIndex produced ${segments.size} segments")
            
            // Check specific high-confidence splits in the FIRST page
            if (pageIndex == 0) {
                val fullText = segments.joinToString(" ") { it.text }
                
                // Verify stuck words from PDF are fixed
                assertTrue("Should fix 'obsidiana y sus'", fullText.contains("obsidiana y sus"))
                assertTrue("Should fix 'tristeza y determinación'", fullText.contains("tristeza y determinación"))
                
                Log.i("FLOW_TEST", "[STEP 3: VERIFIED] PDF Stuck words ARE FIXED in memory")
            }
            
            totalPages++
        }
        
        assertTrue("Should process at least some pages", totalPages > 0)
        
        // DUMP LOGS TO LOGCAT
        com.example.cititor.debug.DiagnosticMonitor.dumpToLogcat()
        
        Log.i("FLOW_TEST", "--- FLOW TEST COMPLETED SUCCESSFULLY ---")
        }
    }
}
