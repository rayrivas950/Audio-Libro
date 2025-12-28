package com.example.cititor.domain.sanitizer

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cititor.data.text_extractor.PdfExtractor
import com.example.cititor.domain.analyzer.ConsistencyAuditor
import com.example.cititor.domain.dictionary.DictionaryManager
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class PdfDebugInstrumentationTest {

    private lateinit var context: Context
    private lateinit var pdfExtractor: PdfExtractor
    private lateinit var dictionaryManager: DictionaryManager
    private lateinit var textSanitizer: TextSanitizer
    private lateinit var consistencyAuditor: ConsistencyAuditor

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        pdfExtractor = PdfExtractor()
        dictionaryManager = DictionaryManager(context)
        textSanitizer = TextSanitizer(dictionaryManager)
        consistencyAuditor = ConsistencyAuditor(dictionaryManager)
    }

    @Test
    fun debugPdfExtractionAndSanitization() {
        // 1. Copy PDF from assets to a real file on the device
        val assetName = "torture_test.pdf"
        val tempFile = File(context.cacheDir, assetName)
        val testContext = InstrumentationRegistry.getInstrumentation().context
        testContext.assets.open(assetName).use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        
        val uri = Uri.fromFile(tempFile)
        Log.d("PDF_TEST", "Testing PDF: ${tempFile.absolutePath}")

        // 2. Extract and Sanitize
        val rawResult = StringBuilder()
        
        // Simulating the flow of BookProcessingWorker
        // Using runBlocking (or similar) to wait for suspension
        kotlinx.coroutines.runBlocking {
            pdfExtractor.extractPages(context, uri) { pageIndex, rawText ->
                Log.d("PDF_TEST", "--- PAGE $pageIndex (RAW) ---")
                Log.d("PDF_TEST", rawText)
                
                val sanitized = textSanitizer.sanitize(rawText)
                val audited = consistencyAuditor.auditAndRepair(sanitized)
                Log.d("PDF_TEST", "--- PAGE $pageIndex (AUDITED) ---")
                Log.d("PDF_TEST", audited)
                
                rawResult.append(audited)
            }
        }
        
        Log.d("PDF_TEST", "Finished processing ${tempFile.name}")
    }
}
