package com.example.cititor.domain

import com.example.cititor.domain.analyzer.TextAnalyzer
import com.example.cititor.domain.analyzer.character.CharacterRegistry
import com.example.cititor.domain.sanitizer.TextSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPipelineTest {

    @Test
    fun `test paragraph preservation through the whole pipeline`() {
        val rawText = "This is a very long line that should definitely be joined with the next one because it is part of the same paragraph and it is long enough to not be a title\nand this is the continuation of that very long line that we were talking about.\n\nSecond paragraph starts here."
        val sanitizedText = TextSanitizer.sanitize(rawText)
        
        assertTrue("Sanitizer should preserve double newlines", sanitizedText.contains("\n\n"))
        
        val registry = CharacterRegistry()
        val (segments, _) = TextAnalyzer.analyze(sanitizedText, registry)
        val reconstructedText = segments.joinToString("") { it.text }
        
        val expected = "This is a very long line that should definitely be joined with the next one because it is part of the same paragraph and it is long enough to not be a title and this is the continuation of that very long line that we were talking about.\n\nSecond paragraph starts here."
        assertEquals("The final text should have the paragraph break", expected, reconstructedText)
    }

    @Test
    fun `test chapter and roman numeral scenario`() {
        // Scenario based on "The Witcher"
        val rawText = "La voz de la razón 2\n\nI\n\n—Geralt."
        val sanitized = TextSanitizer.sanitize(rawText)
        
        // We want to ensure the \n\n are preserved for pauses
        assertTrue("Should have double newlines after chapter name", sanitized.contains("razón 2\n\n"))
        assertTrue("Should have double newlines after Roman numeral", sanitized.contains("I\n\n"))
        
        val (segments, _) = TextAnalyzer.analyze(sanitized, CharacterRegistry())
        
        // We expect segments that preserve the structure
        val reconstructed = segments.joinToString("") { it.text }
        
        println("--- DEBUG CHAPTER TEST ---")
        println("Sanitized:\n'$sanitized'")
        println("Reconstructed:\n'$reconstructed'")
        println("--------------------------")
        
        // Final Assertion
        // Sanitizer normalizes — to -
        // Now it's split into 3 segments: "La voz de la razón 2\n\n", "I\n\n", "-Geralt."
        val expected = "La voz de la razón 2\n\nI\n\n-Geralt."
        assertEquals("Structure should be preserved", expected, reconstructed)
        
        val romanSegment = segments.find { it.text.trim() == "I" }
        assertTrue("Should identify the Roman numeral line", romanSegment != null)
        assertEquals("Roman numeral should be marked as CHAPTER_INDICATOR", 
            com.example.cititor.domain.model.NarrationStyle.CHAPTER_INDICATOR, 
            (romanSegment as? com.example.cititor.domain.model.NarrationSegment)?.style)
    }

    @Test
    fun `test technical manual list items should NOT be silenced`() {
        // Scenario: Technical manual with steps
        val rawText = "1. Paso uno\n2. Paso dos\n\n3) Paso tres\n\nI. Introduction"
        val sanitized = TextSanitizer.sanitize(rawText)
        val (segments, _) = TextAnalyzer.analyze(sanitized, CharacterRegistry())
        
        // None of these should be CHAPTER_INDICATOR because they contain more than just the number
        segments.forEach { segment ->
            if (segment is com.example.cititor.domain.model.NarrationSegment) {
                assertTrue("List item '${segment.text.trim()}' should NOT be a chapter indicator", 
                    segment.style != com.example.cititor.domain.model.NarrationStyle.CHAPTER_INDICATOR)
            }
        }
    }

    @Test
    fun `test standalone indicators with punctuation SHOULD be silenced`() {
        val rawText = "I.\n\n1)\n\n2."
        val sanitized = TextSanitizer.sanitize(rawText)
        val (segments, _) = TextAnalyzer.analyze(sanitized, CharacterRegistry())
        
        assertEquals("Should have 3 segments", 3, segments.size)
        segments.forEach { segment ->
            assertTrue("Standalone indicator '${segment.text.trim()}' SHOULD be a chapter indicator", 
                (segment as com.example.cititor.domain.model.NarrationSegment).style == com.example.cititor.domain.model.NarrationStyle.CHAPTER_INDICATOR)
        }
    }

    @Test
    fun `test paragraph healing for broken sentences`() {
        // Scenario: PDF with physical line break in middle of sentence
        val rawText = "caminando silenciosamente, deslizándose por la\n\nhabitación como un espectro"
        val sanitized = TextSanitizer.sanitize(rawText)
        println("DEBUG HEALING: '$sanitized'")
        assertEquals("Should heal broken sentence even with double newline", 
            "caminando silenciosamente, deslizándose por la habitación como un espectro", sanitized)
    }

    @Test
    fun `test chapter title separation`() {
        // Scenario: Chapter title followed by text
        val rawText = "La voz de la razón 1\nVino a él al romper el alba."
        val sanitized = TextSanitizer.sanitize(rawText)
        println("DEBUG CHAPTER: '$sanitized'")
        assertTrue("Should separate chapter title with double newline", 
            sanitized.contains("razón 1\n\nVino"))
    }
}
