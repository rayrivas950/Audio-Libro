package com.example.cititor.domain.sanitizer

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class TextSanitizerTest {

    @Test
    fun `test dialogue protection - should not join lines starting with em-dash`() {
        val text = "—Posada busco para la noche.\n—Pues no hay —refunfuñó el ventero."
        val sanitized = TextSanitizer.sanitize(text)
        
        // It should contain \n\n to separate paragraphs
        assertTrue("Dialogue lines should be separated by double newline", sanitized.contains("\n\n"))
        // It should NOT contain them joined by a space
        assertFalse("Dialogue lines should not be joined by a space", sanitized.contains("noche. —Pues"))
    }

    @Test
    fun `test paragraph healing - should preserve double newlines`() {
        val text = "Párrafo 1.\n\nPárrafo 2."
        val sanitized = TextSanitizer.sanitize(text)
        
        assertTrue("Should preserve paragraph breaks", sanitized.contains("Párrafo 1.\n\nPárrafo 2."))
    }

    @Test
    fun `test word joining - should preserve spaces between joined words`() {
        val text = "esto es un\ntexto cortado."
        val sanitized = TextSanitizer.sanitize(text)
        
        assertTrue("Should contain space between joined words", sanitized.contains("un texto"))
    }

    @Test
    fun `test spaced name protection - should not collapse normal words`() {
        val text = "EL EXTRAÑO CAMINABA"
        val sanitized = TextSanitizer.sanitize(text)
        
        assertTrue("Should not collapse EL EXTRAÑO", sanitized.contains("EL EXTRAÑO"))
    }

    @Test
    fun `test witcher regression - should preserve spaces and paragraphs in Witcher sample`() {
        // Simulating the "dirty" text seen in some extractions (EPUB/PDF glitches)
        // Note: Some spaces are missing here to see if we can "heal" them or at least not make them worse.
        // Actually, the goal is to ENSURE that if the input has spaces, we don't remove them,
        // and if it has paragraph breaks, we don't join them incorrectly.
        
        val input = """
            El brujo
            I
            
            Después dijeron que aquel hombre había venido desde el norte por la Puerta de los Cordeleros.
            Entró a pie, llevando de las riendas a su caballo.
            Era por la tarde y los tenderetes de los cordeleros y de los talabarteros estaban ya cerrados y la callejuela se encontraba vacía.
            La tarde era calurosa pero aquel hombre traía un capote negro sobre los hombros.
            Llamaba la atención.
            
            Se detuvo ante la venta del Viejo Narakort, se mantuvo de pie un instante, escuchó el rumor de las voces.
        """.trimIndent()

        val sanitized = TextSanitizer.sanitize(input)
        
        // Check for paragraph separation (double newline)
        assertTrue("Header 'El brujo' should be separated", sanitized.contains("El brujo\n\nI"))
        assertTrue("Section 'I' should be separated from text", sanitized.contains("I\n\nDespués"))
        
        // Check that sentences starting with Uppercase after a break ARE separate paragraphs 
        // if they are short (like titles or section markers)
        assertTrue("Main paragraph should be separated from second paragraph", 
            sanitized.contains("Llamaba la atención.\n\nSe detuvo"))

        // Check for space preservation (ensure no "porla" or "asu")
        assertTrue("Should contain 'por la'", sanitized.contains("por la"))
        assertTrue("Should contain 'a su'", sanitized.contains("a su"))
        assertTrue("Should contain 'y los'", sanitized.contains("y los"))
    }
}
