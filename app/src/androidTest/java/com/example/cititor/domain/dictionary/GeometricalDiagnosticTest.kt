package com.example.cititor.domain.dictionary

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import com.example.cititor.data.text_extractor.PdfExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

/**
 * Diagnostic test to understand exactly why some lines are being tagged as titles
 * and why others (like "El brujo") are being missed.
 */
class GeometricalDiagnosticTest {

    @Test
    fun diagnosticStructureExtraction() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pdfFile = File(context.cacheDir, "sapkowski_sample.pdf")
        
        // Copy asset to cache if needed
        if (!pdfFile.exists()) {
            context.assets.open("sapkowski_sample.pdf").use { input ->
                FileOutputStream(pdfFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        val extractor = PdfExtractor()
        val uri = Uri.fromFile(pdfFile)

        // Specifically look at the first few pages (where "El brujo" and "I" are)
        for (i in 0..10) {
            Log.e("GEO_DIAGNOSTIC", "---- PAGE $i ----")
            val text = extractor.extractText(context, uri, i)
            
            // We want to see the RAW logs from IndentationAwareStripper
            // Those logs are already in PdfExtractor.kt, they will appear in Logcat.
        }
    }
}
