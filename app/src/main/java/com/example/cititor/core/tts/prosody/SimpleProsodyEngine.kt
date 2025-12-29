package com.example.cititor.core.tts.prosody

import com.example.cititor.domain.model.BookCategory
import com.example.cititor.domain.model.TTSParameters
import com.example.cititor.domain.model.TextSegment

/**
 * A basic implementation of ProsodyEngine that returns neutral parameters.
 */
class SimpleProsodyEngine : ProsodyEngine {
    override fun calculateParameters(
        segment: TextSegment, 
        masterSpeed: Float,
        category: BookCategory
    ): TTSParameters {
        
        var pausePost: Long? = null
        var speedMultiplier = 1.0f
        var volumeMultiplier = 1.0f
        var pitch = 1.0f

        if (segment is com.example.cititor.domain.model.NarrationSegment) {
            if (segment.style == com.example.cititor.domain.model.NarrationStyle.CHAPTER_INDICATOR) {
                // Emphasis for titles: Slower, slightly louder, and a significant pause after.
                pausePost = 1500L
                speedMultiplier = 0.9f
                volumeMultiplier = 1.1f
            }
        }

        return TTSParameters(
            speed = masterSpeed * speedMultiplier,
            pitch = pitch,
            volume = volumeMultiplier,
            emphasis = false,
            pausePost = pausePost
        )
    }
}
