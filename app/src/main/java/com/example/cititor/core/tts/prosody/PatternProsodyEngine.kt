package com.example.cititor.core.tts.prosody

import com.example.cititor.domain.model.DialogueSegment
import com.example.cititor.domain.model.NarrationSegment
import com.example.cititor.domain.model.TTSParameters
import com.example.cititor.domain.model.TextSegment

/**
 * A more advanced ProsodyEngine that applies rules based on text patterns and segment types.
 * This demonstrates the power of the modular architecture.
 */
class PatternProsodyEngine : ProsodyEngine {
    override fun calculateParameters(segment: TextSegment, masterSpeed: Float): TTSParameters {
        var speed = masterSpeed
        var pitch = 1.0f
        
        // 1. Differentiation by segment type
        when (segment) {
            is DialogueSegment -> {
                // Dialogue is typically slightly more dynamic
                speed *= 1.03f 
                pitch = 1.02f
            }
            is NarrationSegment -> {
                // Narration remains at base speed
                speed = masterSpeed
                pitch = 1.0f
            }
        }
        
        // 2. Pattern-based rules
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
        
        // Slow down for very short segments (emphasis/interjections)
        if (trimmed.length < 15 && !trimmed.contains(" ")) {
            speed *= 0.85f
        }

        return TTSParameters(
            speed = speed,
            pitch = pitch
        )
    }
}
