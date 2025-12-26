package com.example.cititor.core.audio.effects

import kotlin.math.PI

/**
 * Modular implementation of a High-Cut (Low Pass) filter.
 * Effectively removes high-frequency "hiss" or artifacts.
 */
class HighCutFilterEffect(
    private val cutoffFreq: Float = 7500f,
    private val sampleRate: Int = 22050
) : AudioEffect {
    override fun process(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        
        val rc = 1.0f / (2.0f * PI.toFloat() * cutoffFreq)
        val dt = 1.0f / sampleRate
        val alpha = dt / (rc + dt)
        
        val result = FloatArray(samples.size)
        var lastValue = 0f
        for (i in samples.indices) {
            val currentValue = alpha * samples[i] + (1 - alpha) * lastValue
            result[i] = currentValue
            lastValue = currentValue
        }
        return result
    }
}
