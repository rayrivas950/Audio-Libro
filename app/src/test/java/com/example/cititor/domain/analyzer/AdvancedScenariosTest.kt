package com.example.cititor.domain.analyzer

import com.example.cititor.domain.model.DialogueSegment
import com.example.cititor.domain.model.NarrationSegment
import com.example.cititor.domain.model.NarrationStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedScenariosTest {

    @Test
    fun `detects thoughts from italics`() {
        val input = "Juan caminaba. <i>Esto no me gusta</i> pensó."
        val (segments, _) = TextAnalyzer.analyze(input)
        
        // Expected: 
        // 1. Narration: "Juan caminaba."
        // 2. Narration(THOUGHT): "Esto no me gusta"
        // 3. Narration: "pensó."
        
        assertEquals(3, segments.size)
        
        val thoughtSegment = segments[1]
        assertTrue(thoughtSegment is NarrationSegment)
        assertEquals("Esto no me gusta", thoughtSegment.text)
        assertEquals(NarrationStyle.THOUGHT, (thoughtSegment as NarrationSegment).style)
    }

    @Test
    fun `preserves stuttering in dialogue`() {
        val input = "—H-hola, ¿q-quién eres? —preguntó temblando."
        val (segments, _) = TextAnalyzer.analyze(input)
        
        val dialogueSegment = segments[0]
        assertTrue(dialogueSegment is DialogueSegment)
        assertEquals("H-hola, ¿q-quién eres?", dialogueSegment.text)
    }

    @Test
    fun `preserves stuttering in narration`() {
        val input = "El motor hizo un sonido extraño: p-p-pum."
        val (segments, _) = TextAnalyzer.analyze(input)
        
        val narrationSegment = segments[0]
        assertTrue(narrationSegment is NarrationSegment)
        assertEquals("El motor hizo un sonido extraño: p-p-pum.", narrationSegment.text)
    }
}
