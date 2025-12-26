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
    fun `test witcher regression full - should preserve dialogue structure and spaces`() {
        val input = """
            El desconocido no era viejo, pero tenía los cabellos completamente blancos. 
            Debajo del abrigo llevaba una raída almilla de cuero. 
            Poco después el posadero trajo una jarra de barro. La jarra estaba desportillada.
            Cuando se quitó el capote todos se dieron cuenta de que llevaba una espada en un cinturón al dorso.
            
            El desconocido no se sentó a la mesa, entre los escasos clientes, continuó de pie delante del
            mostrador, apuntando hacia el posadero con ojos penetrantes. Bebió un trago.
            —Posada busco para la noche.
            —Pues no hay —refunfuñó el ventero mirando las botas del cliente, sucias y llenas de
            polvo—. Preguntad acaso en el Viejo Narakort.
            —Preferiría aquí.
            —No hay. —El ventero reconoció al fin el acento del desconocido. Era de Rivia.
            —Pagaré bien —dijo el extraño muy bajito, como inseguro.
        """.trimIndent()

        val sanitized = TextSanitizer.sanitize(input)
        
        // 1. Dialogue breaks protection
        assertTrue("Dialogue 'Posada busco' should be a new paragraph", sanitized.contains("Bebió un trago.\n\n—Posada"))
        assertTrue("Dialogue 'Pues no hay' should be a new paragraph", sanitized.contains("noche.\n\n—Pues no hay"))
        assertTrue("Dialogue 'Preferiría aquí' should be a new paragraph", sanitized.contains("Narakort.\n\n—Preferiría"))
        
        // 2. Complex punctuation (.—) should separate interventions, 
        // but —. at the end of an acotación might keep narration on same paragraph in some styles.
        // For consistency with the provided text, we expect spaces or sensible breaks.
        assertTrue("Should contain 'polvo—. Preguntad'", sanitized.contains("polvo—. Preguntad"))
        
        // 3. Space preservation (No "debarro", "enun")
        assertTrue("Should contain 'de barro'", sanitized.contains("de barro") || sanitized.contains("de\nbarro") || sanitized.contains("de\n\nbarro"))
        assertTrue("Should contain 'en un'", sanitized.contains("en un"))
    }

    @Test
    fun `test complex punctuation breaks - should break on dot-dash sequence`() {
        val text = "Dijo adiós.—Hola de nuevo."
        val sanitized = TextSanitizer.sanitize(text)
        assertTrue("Should break between dot and dash", sanitized.contains("adiós.\n\n—Hola"))
    }
}
