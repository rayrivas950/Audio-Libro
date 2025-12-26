package com.example.cititor.core.audio.effects

/**
 * Modular implementation of a softening normalization effect.
 */
class NormalizationEffect(private val targetPeak: Float = 0.7f) : AudioEffect {
    override fun process(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        
        var maxAbs = 0f
        for (s in samples) {
            val abs = Math.abs(s)
            if (abs > maxAbs) maxAbs = abs
        }
        
        if (maxAbs < 0.001f) return samples
        
        val gain = targetPeak / maxAbs
        val safeGain = minOf(gain, 2.0f)
        
        val result = FloatArray(samples.size)
        for (i in samples.indices) {
            result[i] = samples[i] * safeGain
        }
        return result
    }
}
