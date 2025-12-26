package com.example.cititor.core.tts.prosody

import com.example.cititor.domain.model.BookCategory
import com.example.cititor.domain.model.DialogueSegment
import com.example.cititor.domain.model.NarrationSegment
import com.example.cititor.domain.model.TTSParameters
import com.example.cititor.domain.model.TextSegment

/**
 * A more advanced ProsodyEngine that applies rules based on text patterns, 
 * segment types, and book categories.
 */
class PatternProsodyEngine : ProsodyEngine {
    override fun calculateParameters(
        segment: TextSegment, 
        masterSpeed: Float,
        category: BookCategory
    ): TTSParameters {
        var speed = masterSpeed
        var pitch = 1.0f
        
        // --- 1. Base Adjustment by Category ---
        when (category) {
            BookCategory.TECHNICAL -> {
                speed *= 0.95f // Clear and steady
                pitch = 0.98f  // Slightly deeper, more formal
            }
            BookCategory.LEGAL -> {
                speed *= 0.90f // Very deliberate
                pitch = 0.95f  // Deep and formal
            }
            BookCategory.EPIC -> {
                speed *= 1.05f // Energetic
                pitch = 1.05f  // Higher dynamic range
            }
            BookCategory.CHILDREN -> {
                speed *= 0.88f // Slow and clear
                pitch = 1.15f  // Higher and friendly
            }
            else -> {} // Fiction/Journalism use master defaults
        }

        // --- 2. Differentiation by segment type ---
        when (segment) {
            is DialogueSegment -> {
                speed *= 1.03f 
                pitch *= 1.02f
            }
            is NarrationSegment -> {
                // Keep base speed for category
            }
        }
        
        // --- 3. Pattern-based rules (Grammar) ---
        val trimmed = segment.text.trim()
        
        // Raise pitch for questions
        if (trimmed.endsWith("?") || trimmed.endsWith("¿")) {
            pitch *= 1.08f
        }
        
        // More intensity for exclamations
        if (trimmed.endsWith("!") || trimmed.endsWith("¡")) {
            speed *= 1.05f
            pitch *= 1.03f
        }
        
        // Slow down for very short interjections
        if (trimmed.length < 15 && !trimmed.contains(" ")) {
            speed *= 0.85f
        }

        return TTSParameters(
            speed = speed,
            pitch = pitch
        )
    }
}
