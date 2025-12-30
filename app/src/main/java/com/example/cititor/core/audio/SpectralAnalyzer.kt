package com.example.cititor.core.audio

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Lightweight Audio Spectrum Analyzer for real-time diagnostics.
 * Estimates energy distribution across Bass, Mids, and Treble bands.
 */
object SpectralAnalyzer {

    data class SpectralReport(
        val bassEnergy: Float,   // 0.0 - 1.0
        val midEnergy: Float,    // 0.0 - 1.0
        val trebleEnergy: Float, // 0.0 - 1.0
        val dominance: String    // "Deep", "Balanced", "Bright", "Thin"
    )

    /**
     * Analyzes audio chunk using simple digital filters.
     * Efficiency: O(N) single pass approximation.
     */
    fun analyze(samples: FloatArray): SpectralReport {
        if (samples.isEmpty()) return SpectralReport(0f, 0f, 0f, "Silent")

        var bassSum = 0.0
        var midSum = 0.0
        var trebleSum = 0.0

        // Simple State Variable Filter approximation
        // Low Pass Coefficient for ~300Hz at 22050Hz
        // alpha = 2 * pi * fc / fs ~= 6.28 * 300 / 22050 ~= 0.085
        val bassAlpha = 0.085f
        
        // High Pass Coefficient for ~4000Hz (Treble)
        // alpha = 0.7
        
        var lowPassState = 0.0f
        var prevSample = 0.0f

        for (sample in samples) {
            // 1. Low Pass Filter (Extract Bass)
            lowPassState += bassAlpha * (sample - lowPassState)
            val bassComponent = lowPassState
            
            // 2. High Frequency difference (Treble proxy)
            // Using simple difference (derivative) as high-pass
            val trebleComponent = sample - prevSample
            
            // 3. Mids are whatever remains (Sample - Bass - Treble-ish)
            // Ideally: Mid = Sample - Bass - HighPass(Sample)
            // Simplified: Mid = Sample - BassComponent
             val midComponent = sample - bassComponent
            
            bassSum += bassComponent * bassComponent
            midSum += midComponent * midComponent
            trebleSum += trebleComponent * trebleComponent
            
            prevSample = sample
        }

        // Normalize RMS
        val size = samples.size.toFloat()
        val bassRms = sqrt(bassSum / size).toFloat()
        val midRms = sqrt(midSum / size).toFloat()
        val trebleRms = sqrt(trebleSum / size).toFloat()

        // Relative Distribution (Score)
        // We normalize them against a "standard voice" expectation to give useful 0-1 scores
        // Voice expectation: Mid > Bass > Treble
        
        // Return raw RMS for now, user can see relative values
        val total = bassRms + midRms + trebleRms + 0.0001f
        
        val bassRel = bassRms / total
        val midRel = midRms / total
        val trebleRel = trebleRms / total
        
        val dominance = when {
            bassRel > 0.4 -> "Deep/Boomy"
            trebleRel > 0.3 -> "Bright/Hissy"
            midRel > 0.6 -> "Radio/Phone"
            else -> "Balanced"
        }

        return SpectralReport(bassRel, midRel, trebleRel, dominance)
    }
}
