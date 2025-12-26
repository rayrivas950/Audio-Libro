package com.example.cititor.core.audio.effects

/**
 * Interface for atomic audio effects that can be chained.
 */
interface AudioEffect {
    /**
     * Processes a float array of audio samples and returns the processed array.
     */
    fun process(samples: FloatArray): FloatArray
}
