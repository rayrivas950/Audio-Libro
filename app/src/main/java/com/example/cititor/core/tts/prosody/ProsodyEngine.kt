package com.example.cititor.core.tts.prosody

import com.example.cititor.domain.model.TTSParameters
import com.example.cititor.domain.model.TextSegment

/**
 * Interface for the engine that decides how a text segment should be voiced.
 */
interface ProsodyEngine {
    /**
     * Calculates the TTS parameters for a given segment.
     */
    fun calculateParameters(segment: TextSegment, masterSpeed: Float): TTSParameters
}
