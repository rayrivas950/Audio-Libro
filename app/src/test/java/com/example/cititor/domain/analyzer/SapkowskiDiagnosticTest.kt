package com.example.cititor.domain.analyzer

import com.example.cititor.domain.model.NarrationSegment
import com.example.cititor.domain.model.NarrationStyle
import com.example.cititor.domain.sanitizer.TextSanitizer
import com.example.cititor.domain.dictionary.DictionaryManager
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class SapkowskiDiagnosticTest {

    private lateinit var sanitizer: TextSanitizer
    private lateinit var analyzer: TextAnalyzer
    private val dictionaryManager = mockk<DictionaryManager>(relaxed = true)

    @Before
    fun setup() {
        io.mockk.mockkStatic(android.util.Log::class)
        io.mockk.every { android.util.Log.d(any(), any()) } returns 0
        
        val consistencyAuditor = mockk<ConsistencyAuditor>(relaxed = true)
        io.mockk.every { consistencyAuditor.auditAndRepair(any(), any()) } answers { firstArg() }
        
        sanitizer = TextSanitizer(dictionaryManager)
        analyzer = TextAnalyzer(
            textSanitizer = sanitizer,
            dialogueResolver = mockk(relaxed = true),
            intentionAnalyzer = mockk(relaxed = true),
            consistencyAuditor = consistencyAuditor
        )
    }

    @Test
    fun `diagnostic Sapkowski dialogue with complex markers`() {
        // Line from the image: "—Claváis esto en las tabernas y en los cruces de caminos —dijo con voz queda—. ¿Es verdad lo que pone aquí?"
        // This is clearly a dialogue but let's see if the sanitizer/analyzer treats it well
        // We simulate the extractor injecting markers
        
        // CASE 1: The dialogue is erroneously marked as [GEOMETRIC_TITLE] by the extractor
        // We want to see if the analyzer can STILL recover it.
        val problematicInput = "[GEOMETRIC_TITLE] —Claváis esto en las tabernas y en los cruces de caminos —dijo con voz queda—. ¿Es verdad lo que pone aquí?"
        
        val segments = analyzer.analyze(problematicInput)
        val firstSegment = segments[0] as NarrationSegment
        
        println("ANALYSIS RESULT:")
        println("Text: ${firstSegment.text}")
        println("Style: ${firstSegment.style}")
        
        // If it's still CHAPTER_INDICATOR, then the analyzer is trusting the marker too much.
        // We should fix the analyzer to double check markers.
    }
}
