package com.example.cititor.domain

import com.example.cititor.domain.analyzer.TextAnalyzer
import com.example.cititor.domain.analyzer.character.CharacterRegistry
import com.example.cititor.domain.sanitizer.TextSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test
import com.example.cititor.domain.model.DialogueSegment
import com.example.cititor.domain.model.NarrationSegment

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
    fun `test chapter title separation`() {
        // The title "La voz de la razón 1" should be separated from the text
        val rawText = "La voz de la razón 1\nVino a él por la mañana."
        val sanitizedText = TextSanitizer.sanitize(rawText)
        
        // Should have a double newline between title and text
        assertTrue("Sanitizer should separate title with double newline", sanitizedText.contains("\n\n"))
        
        val registry = CharacterRegistry()
        val (segments, _) = TextAnalyzer.analyze(sanitizedText, registry)
        
        // Title is one segment, then \n\n, then the sentence
        // But wait, analyze splits by intelligent segments too.
        // "La voz de la razón 1\n\n" -> Neutral
        // "Vino a él por la mañana." -> Neutral
        
        val titleSegment = segments.find { it.text.contains("razón 1") }
        assertNotNull("Title segment should exist", titleSegment)
        assertTrue("Title should have double newline", titleSegment!!.text.endsWith("\n\n"))
    }

    @Test
    fun `test intelligent segmentation by punctuation`() {
        val rawText = "Entró a pie, llevando de las riendas a su caballo. Era por la tarde."
        val (segments, _) = TextAnalyzer.analyze(rawText, CharacterRegistry())
        
        // Should split into:
        // 1. "Entró a pie, "
        // 2. "llevando de las riendas a su caballo. "
        // 3. "Era por la tarde."
        
        assertEquals("Should split into 3 segments", 3, segments.size)
        assertTrue(segments[0].text.contains(","))
        assertTrue(segments[1].text.contains("."))
    }

    @Test
    fun `test em-dash preservation and dialogue detection`() {
        val rawText = "—Geralt —dijo ella."
        val (segments, _) = TextAnalyzer.analyze(rawText, CharacterRegistry())
        
        // Should have:
        // 1. Dialogue: "—Geralt "
        // 2. Narration: "—dijo ella."
        
        val dialogue = segments.filterIsInstance<DialogueSegment>()
        val narration = segments.filterIsInstance<NarrationSegment>()
        
        assertEquals("Should have 1 dialogue segment", 1, dialogue.size)
        assertTrue("Dialogue should keep em-dash", dialogue[0].text.startsWith("—"))
        
        assertEquals("Should have 1 narration segment (tag)", 1, narration.size)
        assertTrue("Tag should keep em-dash", narration[0].text.startsWith("—"))
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
        // Sanitizer NO LONGER normalizes — to -
        // Now it's split into 3 segments: "La voz de la razón 2\n\n", "I\n\n", "—Geralt."
        val expected = "La voz de la razón 2\n\nI\n\n—Geralt."
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
    fun `test single quote thought detection`() {
        val rawText = "'Te tendría que matar', pensó Pate."
        val (segments, _) = TextAnalyzer.analyze(rawText, CharacterRegistry())
        
        // Should have:
        // 1. Narration (Thought): "'Te tendría que matar'"
        // 2. Narration (Neutral): ", pensó Pate."
        
        val thoughts = segments.filterIsInstance<NarrationSegment>().filter { it.style == com.example.cititor.domain.model.NarrationStyle.THOUGHT }
        assertEquals("Should have 1 thought segment", 1, thoughts.size)
        assertTrue("Thought should contain the text", thoughts[0].text.contains("matar"))
    }

    @Test
    fun `test list segmentation with consecutive commas`() {
        val rawText = "Trajo manzanas, peras, uvas y naranjas."
        val (segments, _) = TextAnalyzer.analyze(rawText, CharacterRegistry())
        
        // Should split by commas and conjunctions
        // 1. "Trajo manzanas, "
        // 2. "peras, "
        // 3. "uvas "
        // 4. "y naranjas."
        
        assertTrue("Should have multiple segments for the list", segments.size >= 3)
        assertTrue("First segment should end with comma", segments[0].text.trim().endsWith(","))
        assertTrue("Second segment should end with comma", segments[1].text.trim().endsWith(","))
    }
}
