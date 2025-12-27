package com.example.cititor.core.audio.effects

/**
 * A simple first-order Low Pass Filter (RC filter simulation) to remove high-frequency digital noise.
 * Useful for "warming up" TTS voices that sound metallic or "fried".
 */
class LowPassFilterEffect(private val smoothingFactor: Float = 0.5f) : AudioEffect {
    
    // Previous sample for recursive filtering
    private var lastSample: Float = 0f

    override fun process(samples: FloatArray): FloatArray {
        val output = FloatArray(samples.size)
        // Adjust smoothing factor based on desired result.
        // Higher alpha (0-1) = Less filtering (More high freq passes)
        // Lower alpha = More filtering (Muffled)
        // 0.5 is a gentle warmth filter.
        
        for (i in samples.indices) {
            val current = samples[i]
            // y[i] = y[i-1] + alpha * (x[i] - y[i-1])
            // Equivalent to: y[i] = alpha * x[i] + (1 - alpha) * y[i-1]
            lastSample = lastSample + smoothingFactor * (current - lastSample)
            output[i] = lastSample
        }
        return output
    }
}
