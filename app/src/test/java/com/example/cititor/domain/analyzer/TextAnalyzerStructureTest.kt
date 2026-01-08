package com.example.cititor.domain.analyzer

import com.example.cititor.domain.model.NarrationSegment
import com.example.cititor.domain.model.NarrationStyle
import com.example.cititor.domain.sanitizer.TextSanitizer
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TextAnalyzerStructureTest {

    private lateinit var textAnalyzer: TextAnalyzer
    private val textSanitizer: TextSanitizer = mockk()
    private val characterDetector: CharacterDetector = mockk()
    private val intentionAnalyzer: IntentionAnalyzer = mockk()
    private val consistencyAuditor: ConsistencyAuditor = mockk()

    @Before
    fun setup() {
        io.mockk.mockkStatic(android.util.Log::class)
        every { android.util.Log.d(any(), any()) } returns 0
        every { android.util.Log.e(any(), any(), any()) } returns 0
        
        textAnalyzer = TextAnalyzer(
            textSanitizer,
            characterDetector,
            intentionAnalyzer,
            consistencyAuditor
        )
        
        // Default behavior for mocks
        every { consistencyAuditor.auditAndRepair(any()) } answers { it.invocation.args[0] as String }
    }

    @Test
    fun `test title markers are correctly mapped to styles`() {
        val rawText = "[TITLE_L]Main Title[/TITLE_L]\n\n[TITLE_M]Sub Title[/TITLE_M]\n\nNormal paragraph."
        
        every { textSanitizer.sanitize(any(), any()) } returns rawText
        
        val segments = textAnalyzer.analyze("irrelevant", isEpub = true)
        
        assertEquals(3, segments.size)
        
        val titleLarge = segments[0] as NarrationSegment
        assertEquals("Main Title", titleLarge.text)
        assertEquals(NarrationStyle.TITLE_LARGE, titleLarge.style)
        
        val titleMedium = segments[1] as NarrationSegment
        assertEquals("Sub Title", titleMedium.text)
        assertEquals(NarrationStyle.TITLE_MEDIUM, titleMedium.style)
    }

    @Test
    fun `test internal newline in title split`() {
        // This verifies that if EpubExtractor injects a newline, TextAnalyzer splits it
        val rawText = "[TITLE_L]Title Part 1\nTitle Part 2[/TITLE_L]"
        
        every { textSanitizer.sanitize(any(), any()) } returns rawText
        
        val segments = textAnalyzer.analyze("irrelevant", isEpub = true)
        
        assertEquals(2, segments.size)
        assertEquals(NarrationStyle.TITLE_LARGE, (segments[0] as NarrationSegment).style)
        assertEquals(NarrationStyle.`NEUTRAL`, (segments[1] as NarrationSegment).style)
    }
}
