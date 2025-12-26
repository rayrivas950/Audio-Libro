package com.example.cititor.core.audio

import be.tarsos.dsp.AudioDispatcher
import be.tarsos.dsp.AudioEvent
import be.tarsos.dsp.AudioProcessor
import be.tarsos.dsp.io.TarsosDSPAudioFloatConverter
import be.tarsos.dsp.io.TarsosDSPAudioFormat
import be.tarsos.dsp.pitch.PitchProcessor
import be.tarsos.dsp.resample.RateTransposer

class AudioEffectProcessor {

    /**
     * Applies a high-cut filter, warmth EQ, and soft normalization.
     */
    fun applyNaturalization(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        
        var processed = samples
        
        // 1. High-Cut Filter (Remove "frito" / high-frequency artifacts)
        // Cutoff at ~7.5kHz is good for voice to maintain clarity but remove hiss
        processed = applyHighCutFilter(processed, cutoffFreq = 7500f)

        // 2. Warmth EQ (Simple Low Shelf filter)
        processed = applyWarmthEQ(processed)
        
        // 3. Soft Normalization
        processed = applySoftNormalization(processed)
        
        return processed
    }

    private fun applyHighCutFilter(samples: FloatArray, cutoffFreq: Float, sampleRate: Int = 22050): FloatArray {
        // First-order Low Pass Filter
        val rc = 1.0f / (2.0f * Math.PI.toFloat() * cutoffFreq)
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

    private fun applyWarmthEQ(samples: FloatArray): FloatArray {
        // Simple first-order low-shelf filter approximation
        // boost = 1.25 (+2dB approx), cutoff around 200Hz
        val alpha = 0.1f 
        val boost = 1.25f
        val result = FloatArray(samples.size)
        var lowPass = 0f
        
        for (i in samples.indices) {
            lowPass = alpha * samples[i] + (1 - alpha) * lowPass
            result[i] = samples[i] + (lowPass * (boost - 1.0f))
        }
        return result
    }

    private fun applySoftNormalization(samples: FloatArray): FloatArray {
        var maxAbs = 0f
        for (s in samples) {
            val abs = Math.abs(s)
            if (abs > maxAbs) maxAbs = abs
        }
        
        if (maxAbs < 0.001f) return samples // Too quiet or silent
        
        val targetPeak = 0.7f // -3dB
        val gain = targetPeak / maxAbs
        
        // Limit gain to avoid excessive noise floor boost
        val safeGain = minOf(gain, 2.0f) 
        
        val result = FloatArray(samples.size)
        for (i in samples.indices) {
            result[i] = samples[i] * safeGain
        }
        return result
    }

    /**
     * Applies pitch shifting to the audio samples.
     * @param samples The input audio samples (float array).
     * @param sampleRate The sample rate of the audio.
     * @param pitchFactor The factor to shift pitch (e.g., 0.8 for lower, 1.2 for higher). 1.0 is neutral.
     * @return The processed audio samples.
     */
    fun applyPitchShift(samples: FloatArray, sampleRate: Int, pitchFactor: Double): FloatArray {
        if (pitchFactor == 1.0 || samples.isEmpty()) return samples

        // Using linear interpolation for resampling (changes pitch and speed)
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
