package com.example.cititor.domain.analyzer

import com.example.cititor.domain.model.NarrationSegment
import com.example.cititor.domain.model.NarrationStyle
import com.example.cititor.domain.sanitizer.TextSanitizer
import com.example.cititor.domain.dictionary.DictionaryManager
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PipelineStructuralTest {

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

    @org.junit.After
    fun teardown() {
        io.mockk.unmockkStatic(android.util.Log::class)
    }

    @Test
    fun `test semantic title detection (no period rule)`() {
        val rawFromPdf = "[GEOMETRIC_TITLE] EL BRUJO I\n\nDespués dijeron que aquel hombre había venido\ndesde el norte por la Puerta de los Cordeleros."
        
        val segments = analyzer.analyze(rawFromPdf)
        
        val firstSegment = segments[0] as NarrationSegment
        assertEquals("EL BRUJO I", firstSegment.text)
        assertEquals(NarrationStyle.CHAPTER_INDICATOR, firstSegment.style)
    }

    @Test
    fun `test short line with period is NEUTRAL`() {
        // "Arturo asintió lentamente." is short but has a period, so it should be NEUTRAL
        val rawNormal = "Arturo asinti\u00f3 lentamente."
        
        val segments = analyzer.analyze(rawNormal)
        
        val firstSegment = segments[0] as NarrationSegment
        assertEquals(NarrationStyle.NEUTRAL, firstSegment.style)
    }

    @Test
    fun `test short dialogue is NEUTRAL even without period`() {
        // Dialogues starting with \u2014 or - should not be titles
        val rawDialogue = "\u2014\u00bfQu\u00e9 va a ser?"
        
        val segments = analyzer.analyze(rawDialogue)
        
        val firstSegment = segments[0] as NarrationSegment
        assertEquals(NarrationStyle.NEUTRAL, firstSegment.style)
    }

    @Test
    fun `test short lines are not joined to paragraphs`() {
        val rawWithShortLine = "I\n\nEste es un parrafo normal que tiene varias\nlineas continuas."
        
        val segments = analyzer.analyze(rawWithShortLine)
        
        assertEquals("I", (segments[0] as NarrationSegment).text)
    }
}
