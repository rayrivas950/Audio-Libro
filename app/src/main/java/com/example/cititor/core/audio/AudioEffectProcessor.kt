package com.example.cititor.core.audio

import com.example.cititor.core.audio.effects.AudioEffect

/**
 * Orchestrates a chain of audio effects.
 */
class AudioEffectProcessor(
    private val effects: List<AudioEffect> = emptyList()
) {
    private val dynamicEffects = mutableMapOf<com.example.cititor.domain.model.ProsodyIntention, List<AudioEffect>>()

    init {
        // Register intention-specific effects
        dynamicEffects[com.example.cititor.domain.model.ProsodyIntention.SHOUT] = listOf(
            com.example.cititor.core.audio.effects.DynamicLimiterEffect(threshold = 0.85f)
        )
        dynamicEffects[com.example.cititor.domain.model.ProsodyIntention.WHISPER] = listOf(
            com.example.cititor.core.audio.effects.WhisperFilterEffect(cutoff = 2500f)
        )
    }

    /**
     * Applies the chain of registered naturalization effects based on intention.
     */
    fun applyNaturalization(
        samples: FloatArray, 
        intention: com.example.cititor.domain.model.ProsodyIntention = com.example.cititor.domain.model.ProsodyIntention.NEUTRAL
    ): FloatArray {
        if (samples.isEmpty()) return samples
        
        var processed = samples
        
        // 1. Apply intention-specific effects first (e.g., whisper texture)
        dynamicEffects[intention]?.forEach { effect ->
            processed = effect.process(processed)
        }

        // 2. Apply master chain (normalization, high-cut)
        for (effect in effects) {
            processed = effect.process(processed)
        }
        
        return processed
    }

    /**
     * Applies pitch shifting to the audio samples using linear interpolation.
     * Note: This also affects speed if not compensated.
     * 
     * @param samples The input audio samples (float array).
     * @param pitchFactor The factor to shift pitch (e.g., 0.8 for lower, 1.2 for higher). 1.0 is neutral.
     * @return The processed audio samples.
     */
    fun applyPitchShift(samples: FloatArray, pitchFactor: Double): FloatArray {
        if (pitchFactor == 1.0 || samples.isEmpty()) return samples

        // New size = Original size / pitchFactor
        val newSize = (samples.size / pitchFactor).toInt()
        val result = FloatArray(newSize)

        for (i in 0 until newSize) {
            val originalIndex = i * pitchFactor
            val index1 = originalIndex.toInt()
            val index2 = minOf(index1 + 1, samples.size - 1)
            val fraction = (originalIndex - index1).toFloat()

            // Linear interpolation
            result[i] = samples[index1] * (1.0f - fraction) + samples[index2] * fraction
        }

        return result
    }
}
