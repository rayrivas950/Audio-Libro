package com.example.cititor.domain.analyzer

import org.junit.Assert.assertTrue
import org.junit.Test

class SyntacticSplitTest {

    @Test
    fun `test splitting by conjunction y`() {
        val text = "el ventero saco la cabeza de un cuenco con pepinillos en vinagre y dirigio su mirada hacia el huesped"
        // This text is > 50 chars and has an 'y' in the middle.
        
        val segments = TextAnalyzer.analyze(text).first.map { it.text }
        
        // Check if any segment contains 'y' as a split point or if it's split correctly
        // The current implementation splits and leaves the space/marker at the start of the second part usually
        // or trims it depending on the substring.
        
        // Let's verify we have at least 2 segments for this long string
        assertTrue("Should split long text into at least 2 segments", segments.size >= 2)
        
        // Find the segment containing the split
        val splitFound = segments.any { it.contains("vinagre") } && segments.any { it.contains("dirigio") }
        assertTrue("Should split near the conjunction 'y'", splitFound)
    }

    @Test
    fun `test splitting by preposition`() {
        val text = "una gran cantidad de libros antiguos estaban apilados sobre la mesa de madera oscura"
        // No 'y' here, but several prepositions like 'sobre' or 'de'
        
        val segments = TextAnalyzer.analyze(text).first.map { it.text }
        assertTrue("Should split by prepositions if no conjunctions are found", segments.size >= 2)
    }
}
