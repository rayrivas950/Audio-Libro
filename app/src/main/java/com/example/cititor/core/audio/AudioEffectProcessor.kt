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
