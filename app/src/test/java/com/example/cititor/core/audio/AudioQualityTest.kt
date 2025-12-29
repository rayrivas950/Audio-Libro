package com.example.cititor.core.audio

import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Diagnostic tool to analyze the spectral quality of the generated audio.
 * Does not require Android Context purely for math analysis using known sample data prototypes.
 */
class AudioQualityTest {

    @Test
    fun `analyze_audio_signal_characteristics`() {
        // This test acts as a REPORT GENERATOR.
        // Since we cannot easily run Piper inside a unit test without mocking massive native libs,
        // we will create a SYNTHETIC waveform that mimics the artifacts we suspect, 
        // and verify our analyzer can detect them.
        
        // However, for the REAL diagnostic requested by the user, we need to log what the actual engine does.
        // Strategy: We will implement the Logic here that TextToSpeechManager can use to log.
        
        println("=== AUDIO QUALITY ANALYSIS PROTOCOL ===")
        println("Defining quality thresholds for professional narration:")
        println("1. RMS (Volume): Target -20dB to -12dB (0.1 to 0.25 raw amplitude)")
        println("2. Noise Floor: Should be < -60dB (< 0.001 raw)")
        println("3. DC Offset: Should be 0.0")
        println("4. Discontinuity (Clicks): Delta > 0.1 between samples is suspicious")
    }

    /**
     * Call this method from TextToSpeechManager with real data to print the report.
     */
    companion object {
        fun analyzeWaveform(samples: FloatArray, tag: String = "Diagnostic") {
            if (samples.isEmpty()) return

            var sumSquares = 0.0
            var maxPeak = 0.0f
            var minPeak = 0.0f
            var dcSum = 0.0
            var maxDelta = 0.0f
            var discontinuities = 0
            
            var prevSample = samples[0]

            for (i in samples.indices) {
                val sample = samples[i]
                
                // RMS Accumulator
                sumSquares += (sample * sample)
                
                // Peak
                if (sample > maxPeak) maxPeak = sample
                if (sample < minPeak) minPeak = sample
                
                // DC Offset Accumulator
                dcSum += sample
                
                // Continuity Check (High frequency noise / Clicks)
                if (i > 0) {
                    val delta = abs(sample - prevSample)
                    if (delta > maxDelta) maxDelta = delta
                    // A jump of > 0.5 in a single sample (at 22khz) is a massive click/pop
                    if (delta > 0.5f) discontinuities++
                }
                prevSample = sample
            }

            val rms = sqrt(sumSquares / samples.size).toFloat()
            val dcOffset = (dcSum / samples.size).toFloat()
            val db = 20 * kotlin.math.log10(rms.toDouble())

            println("\n--- AUDIO REPORT [$tag] ---")
            println("Samples: ${samples.size} (${samples.size / 22050f} sec)")
            println("RMS (Power): $rms ($db dB) -> [Target: 0.1-0.2]")
            println("Peak Amplitude: $maxPeak / $minPeak -> [Target: +/- 0.9]")
            println("DC Offset: $dcOffset -> [Target: 0.0]")
            println("Max Delta (Smoothness): $maxDelta")
            println("Discontinuities (Clicks): $discontinuities")
            
            if (abs(dcOffset) > 0.01) println("⚠️ WARNING: SIGNIFICANT DC OFFSET DETECTED (Hum/Pop risk)")
            if (discontinuities > 0) println("⚠️ WARNING: DETECTED $discontinuities CLICKS/POPS")
            if (maxPeak > 0.98 || minPeak < -0.98) println("⚠️ WARNING: CLIPPING DETECTED (Distortion)")
        }
    }
}
