package com.example.cititor.core.audio.effects

/**
 * A filter specialized in giving a "whisper" texture by attenuating high-mid 
 * frequencies and slightly reducing the overall body.
 */
class WhisperFilterEffect(
    private val cutoff: Float = 3000f,
    private val sampleRate: Int = 22050
) : AudioEffect {

    private var lastOut = 0f
    private val alpha: Float

    init {
        // Standard first-order low pass filter coefficient
        val rc = 1.0f / (2.0f * Math.PI.toFloat() * cutoff)
        val dt = 1.0f / sampleRate
        alpha = dt / (rc + dt)
    }

    override fun process(samples: FloatArray): FloatArray {
        val result = FloatArray(samples.size)
        // We add a bit of "air" by mixing original with filtered or just purely filtered for "darker" whisper
        for (i in samples.indices) {
            val out = lastOut + alpha * (samples[i] - lastOut)
            result[i] = out * 0.8f // Damping volume for whispers
            lastOut = out
        }
        return result
    }
}
