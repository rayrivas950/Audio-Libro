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
    fun `test spaced name collapse - should collapse A N D R E Z`() {
        val text = "A N D R E Z estaba allí."
        val sanitized = TextSanitizer.sanitize(text)
        
        assertTrue("Should collapse A N D R E Z", sanitized.contains("ANDREZ"))
    }
}
