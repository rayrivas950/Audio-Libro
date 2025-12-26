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
        return TTSParameters(
            speed = masterSpeed,
            pitch = 1.0f,
            volume = 1.0f,
            emphasis = false,
            pausePost = null
        )
    }
}
