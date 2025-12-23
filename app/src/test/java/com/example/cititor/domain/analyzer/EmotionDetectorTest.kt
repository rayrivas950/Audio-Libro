package com.example.cititor.domain.analyzer

import com.example.cititor.domain.analyzer.emotion.EmotionDetector
import com.example.cititor.domain.model.Emotion
import org.junit.Assert.assertEquals
import org.junit.Test

class EmotionDetectorTest {

    @Test
    fun `detects anger from context in Spanish`() {
        val dialogue = "¡Vete de aquí!"
        val context = "gritó él furioso"
        val (emotion, intensity) = EmotionDetector.detect(dialogue, context, "es")
        
        assertEquals(Emotion.ANGER, emotion)
        assert(intensity > 0.5f)
    }

    @Test
    fun `detects whisper from adverb in Spanish`() {
        val dialogue = "No hagas ruido"
        val context = "dijo suavemente"
        val (emotion, intensity) = EmotionDetector.detect(dialogue, context, "es")
        
        assertEquals(Emotion.WHISPER, emotion)
    }

    @Test
    fun `detects sadness from context in English`() {
        val dialogue = "I miss him so much"
        val context = "she said sadly while crying"
        val (emotion, intensity) = EmotionDetector.detect(dialogue, context, "en")
        
        assertEquals(Emotion.SADNESS, emotion)
        assert(intensity >= 0.8f)
    }

    @Test
    fun `detects surprise from punctuation`() {
        val dialogue = "¡¿Qué es esto?!"
        val context = "dijo Juan" // Neutral context
        val (emotion, intensity) = EmotionDetector.detect(dialogue, context, "es")
        
        assertEquals(Emotion.SURPRISE, emotion)
        assertEquals(1.0f, intensity, 0.0f)
    }
    
    @Test
    fun `defaults to neutral`() {
        val dialogue = "Hola, ¿cómo estás?"
        val context = "dijo María"
        val (emotion, intensity) = EmotionDetector.detect(dialogue, context, "es")
        
        assertEquals(Emotion.NEUTRAL, emotion)
    }
}
