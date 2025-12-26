package com.example.cititor.core.audio.effects

import kotlin.math.abs

/**
 * A simple dynamic limiter to prevent audio clipping (clamping samples between -1.0 and 1.0)
 * and applying a soft knee reduction when samples exceed a threshold.
 */
class DynamicLimiterEffect(
    private val threshold: Float = 0.9f,
    private val release: Float = 0.95f // Simple decay for gain reduction
) : AudioEffect {
    
    private var currentGain = 1.0f

    override fun process(samples: FloatArray): FloatArray {
        val result = FloatArray(samples.size)
        
        for (i in samples.indices) {
            val peak = abs(samples[i])
            
            // If sample exceeds threshold, calculate needed reduction
            val targetGain = if (peak > threshold) {
                threshold / peak
            } else {
                1.0f
            }
            
            // Smooth gain transition (very primitive look-ahead/release)
            if (targetGain < currentGain) {
                currentGain = targetGain // Instant attack
            } else {
                currentGain = currentGain * release + targetGain * (1.0f - release)
            }
            
            result[i] = (samples[i] * currentGain).coerceIn(-1.0f, 1.0f)
        }
        
        return result
    }
}
