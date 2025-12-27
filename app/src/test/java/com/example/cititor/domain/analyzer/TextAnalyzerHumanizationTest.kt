package com.example.cititor.domain.analyzer

import com.example.cititor.domain.model.ProsodyIntention
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class TextAnalyzerHumanizationTest {

    // Mock the dependency
    private val mockDialogueResolver: DialogueResolver = mockk(relaxed = true)
    
    // We use a real IntentionAnalyzer to test the gravity logic
    private val intentionAnalyzer = IntentionAnalyzer()
    
    private val textAnalyzer = TextAnalyzer(mockDialogueResolver, intentionAnalyzer)

    @Test
    fun `Gravity Detector assigns EMPHASIS to sentences with high proper name density`() {
        val text = "El Rey Arturo miró hacia Camelot con tristeza."
        val intention = intentionAnalyzer.identifyIntention(text)
        
        // "Rey", "Arturo", "Camelot" -> 3 capitalized words > 2 -> EMPHASIS
        assertEquals("Should detect gravity in epic sentence", ProsodyIntention.EMPHASIS, intention)
    }

    @Test
    fun `Gravity Detector ignores start of sentence capitalization`() {
        // Text must be long enough (>40 chars) to avoid TENSION trigger
        val text = "La casa era grande y bonita, con un jardín lleno de flores de muchos colores."
        val intention = intentionAnalyzer.identifyIntention(text)
        
        // "La" is start. No other proper names. -> NEUTRAL
        assertEquals("Should be neutral for common sentence", ProsodyIntention.NEUTRAL, intention)
    }

    @Test
    fun `Breathing Logic splits long sentences at connectors`() {
        // Sentence > 20 words
        val longText = "El caballero caminó por el sendero oscuro del bosque antiguo y miró hacia el cielo donde las estrellas brillaban con fuerza." 
        
        val segments = textAnalyzer.analyze(longText)
        
        // Should be split into at least 2 segments due to " y "
        assertTrue("Should split long sentence", segments.size >= 2)
        assertEquals("First segment should end with connector", "El caballero caminó por el sendero oscuro del bosque antiguo y", segments[0].text)
        assertTrue("Second segment should start with space", segments[1].text.startsWith("miró"))
    }
    
    @Test
    fun `Breathing Logic does NOT split short sentences`() {
        val shortText = "Juan compró pan y leche."
        val segments = textAnalyzer.analyze(shortText)
        
        assertEquals("Should not split short sentence", 1, segments.size)
        assertEquals(shortText, segments[0].text)
    }
}
