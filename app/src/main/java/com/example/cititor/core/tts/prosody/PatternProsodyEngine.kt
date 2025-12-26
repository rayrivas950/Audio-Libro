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
                // Technical manuals: slower, neutral pitch, very clear
                speed *= 0.90f 
                pitch = 0.95f 
            }
            BookCategory.LEGAL -> {
                // Legal texts: slow, deep, formal, authoritative
                speed *= 0.85f 
                pitch = 0.90f 
            }
            BookCategory.EPIC -> {
                // Fantasy/Epic: faster, higher pitch, more dramatic
                speed *= 1.08f 
                pitch = 1.05f 
            }
            BookCategory.CHILDREN -> {
                // Children's stories: very slow, high-pitched, friendly
                speed *= 0.82f 
                pitch = 1.15f 
            }
            BookCategory.PHILOSOPHY -> {
                // Philosophy: moderate speed, slightly lower pitch, thoughtful
                speed *= 0.94f
                pitch = 0.97f
            }
            BookCategory.COOKING -> {
                // Cooking: clear, slightly higher speed for steps, neutral pitch
                speed *= 1.02f
                pitch = 1.0f
            }
            else -> {} // Fiction/Journalism use defaults
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
        
        // --- 3. Intention-based Rules (Semantic Map) ---
        when (segment.intention) {
            com.example.cititor.domain.model.ProsodyIntention.WHISPER -> {
                speed *= 0.92f
                pitch *= 0.95f
                // Volume would be adjusted here if supported by Piper, 
                // or via GainEffect in AudioEffectProcessor
            }
            com.example.cititor.domain.model.ProsodyIntention.SHOUT -> {
                speed *= 1.10f
                pitch *= 1.10f
            }
            com.example.cititor.domain.model.ProsodyIntention.SUSPENSE -> {
                speed *= 0.88f
                pitch *= 0.97f
            }
            com.example.cititor.domain.model.ProsodyIntention.THOUGHT -> {
                pitch *= 1.05f
            }
            else -> {}
        }

        // --- 4. Pattern-based rules (Grammar Fallback) ---
        val trimmed = segment.text.trim()
        
        // Raise pitch for questions (if not already handled by intention)
        if (segment.intention == com.example.cititor.domain.model.ProsodyIntention.NEUTRAL) {
            if (trimmed.endsWith("?") || trimmed.endsWith("¿")) {
                pitch *= 1.08f
            }
        }

        return TTSParameters(
            speed = speed,
            pitch = pitch
        )
    }
}
