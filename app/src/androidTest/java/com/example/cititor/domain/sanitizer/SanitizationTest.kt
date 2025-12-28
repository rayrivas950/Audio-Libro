package com.example.cititor.domain.sanitizer

import androidx.test.platform.app.InstrumentationRegistry
import com.example.cititor.domain.dictionary.DictionaryManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SanitizationTest {

    private lateinit var dictionaryManager: DictionaryManager
    private lateinit var textSanitizer: TextSanitizer

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        dictionaryManager = DictionaryManager(context)
        textSanitizer = TextSanitizer(dictionaryManager)
    }

    @Test
    fun testDictionaryLoading() {
        val loaded = dictionaryManager.loadDictionary("es_ES")
        assert(loaded) { "Dictionary failed to load" }
        assert(dictionaryManager.contains("que")) { "Dictionary should contain 'que'" }
    }

    @Test
    fun testStuckWordsCorrection() {
        // Basic stuck words
        assertEquals("y que", textSanitizer.sanitize("yque"))
        assertEquals("o que", textSanitizer.sanitize("oque"))
        assertEquals("pero que", textSanitizer.sanitize("peroque"))
        
        // Proper nouns (should NOT be split if they start with uppercase and connector is single letter)
        assertEquals("Yquemar", textSanitizer.sanitize("Yquemar"))
        assertEquals("Yque", textSanitizer.sanitize("Yque")) // Protected single-letter conector
        
        // Multi-letter connectors starting with Upper should still be split if valid
        // Example: "Peroque" -> "Pero que" (if "Pero" and "que" are in dict)
        // Note: Our current logic only protects single-letter connectors starting with Upper.
    }

    @Test
    fun testMiddleSplitCases() {
        // Cases where the connector is in the middle
        assertEquals("obsidiana y sus", textSanitizer.sanitize("obsidianaysus"))
        assertEquals("eterna y que", textSanitizer.sanitize("eternayque"))
        assertEquals("determinación y por", textSanitizer.sanitize("determinaciónypor"))
        // Check capitalization preservation in middle split
        assertEquals("Obsidiana y sus", textSanitizer.sanitize("Obsidianaysus"))
    }

    @Test
    fun testScreenshotArtifacts() {
        // Cases from the user's screenshot
        assertEquals("y sus", textSanitizer.sanitize("ysus"))
        assertEquals("y que.", textSanitizer.sanitize("yque."))
        assertEquals("eterna. El", textSanitizer.sanitize("eterna.El"))
        assertEquals("¿gritó", textSanitizer.sanitize("?gritó"))
        assertEquals("y por", textSanitizer.sanitize("ypor"))
        assertEquals("y determinación", textSanitizer.sanitize("ydeterminación"))
    }

    @Test
    fun testTortureTestSanitization() {
        // This simulates reading from the torture_test.txt context
        val text = """
            El antiguo castillo de Stormhold se alzaba majestuoso sobre la colina de obsidiana y sus torres desafiaban al cielo gris que amenazaba con una tormenta eterna. 
            El Rey Arturo miró hacia Camelot con una mezcla de tristeza y determinación, sabiendo que la batalla erainevitable yque el destino de todo el reino pendía de un hilo.
        """.trimIndent()
        
        val sanitized = textSanitizer.sanitize(text)
        
        // Check that core corrections were made
        assert(sanitized.contains("y que")) { "Failed to correct 'yque' in complex text" }
        assert(sanitized.contains("Arturo")) { "Failed to preserve proper noun 'Arturo'" }
        assert(sanitized.contains("\n\n")) { "Should preserve paragraph structure" }
    }

    @Test
    fun testSpacingToleranceSimulation() {
        // This simulates what happens when PDFBox fails to add spaces
        val gluedText = "Enunlugar delaMancha decuyonombre noquiero acordarme"
        val sanitized = textSanitizer.sanitize(gluedText)
        
        // El DictionaryManager actual solo separa si empiezan con CONECTORES comunes
        // Vamos a verificar si eso es suficiente o si necesitamos ser más agresivos.
        println("Glued: $gluedText")
        println("Sanitized: $sanitized")
    }
}
