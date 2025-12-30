package com.example.cititor.core.audio.effects

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.PI

/**
 * Parametric 3-Band Equalizer (Bass, Mid, Treble).
 * Allows boosting or cutting specific frequency ranges.
 * 
 * @param lowGainDb Gain for Bass in dB (e.g., +3.0, -10.0).
 * @param midGainDb Gain for Mids in dB.
 * @param highGainDb Gain for Treble in dB.
 */
class ThreeBandEqEffect(
    val lowGainDb: Float = 0f,
    val midGainDb: Float = 0f,
    val highGainDb: Float = 0f
) : AudioEffect {
    
    // Digital Biquad Filter implementation
    // We run 3 filters in series: LowShelf -> Peaking (Mid) -> HighShelf
    
    // Simplification for real-time without library overhead:
    // We will standard Shelving filters hardcoded for voice ranges.
    
    override fun process(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        
        // If neutral, bypass
        if (lowGainDb == 0f && midGainDb == 0f && highGainDb == 0f) return samples
        
        var output = samples.clone()
        
        // 1. Process Bass (Low Shelf @ 200Hz)
        if (lowGainDb != 0f) {
            output = applyBiquad(output, calculateLowShelf(lowGainDb, 200f))
        }
        
        // 2. Process Mids (Peaking @ 1000Hz)
        if (midGainDb != 0f) {
            output = applyBiquad(output, calculatePeaking(midGainDb, 1000f, 1.0f))
        }

        // 3. Process Treble (High Shelf @ 4000Hz)
        if (highGainDb != 0f) {
            output = applyBiquad(output, calculateHighShelf(highGainDb, 4000f))
        }
        
        return output
    }
    
    // --- DSP Kernels ---
    
    data class Coefficients(val a0: Double, val a1: Double, val a2: Double, val b0: Double, val b1: Double, val b2: Double)

    private fun applyBiquad(input: FloatArray, coeffs: Coefficients): FloatArray {
        val out = FloatArray(input.size)
        var x1 = 0.0; var x2 = 0.0
        var y1 = 0.0; var y2 = 0.0
        
        val a0 = coeffs.a0
        val a1 = coeffs.a1 / a0
        val a2 = coeffs.a2 / a0
        val b0 = coeffs.b0 / a0
        val b1 = coeffs.b1 / a0
        val b2 = coeffs.b2 / a0
        
        for (i in input.indices) {
            val x0 = input[i].toDouble()
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            
            out[i] = y0.toFloat()
            
            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
        }
        return out
    }
    
    private fun calculateHighShelf(gainDb: Float, freq: Float): Coefficients {
        val fs = 22050.0
        val A = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * freq / fs
        val alpha = sin(w0) / 2.0 * sqrt((A + 1/A) * (1/0.707 - 1) + 2)
        val cosW = cos(w0)
        
        val b0 = A * ((A + 1) + (A - 1) * cosW + 2 * sqrt(A) * alpha)
        val b1 = -2 * A * ((A - 1) + (A + 1) * cosW)
        val b2 = A * ((A + 1) + (A - 1) * cosW - 2 * sqrt(A) * alpha)
        val a0 = (A + 1) - (A - 1) * cosW + 2 * sqrt(A) * alpha
        val a1 = 2 * ((A - 1) - (A + 1) * cosW)
        val a2 = (A + 1) - (A - 1) * cosW - 2 * sqrt(A) * alpha
        
        return Coefficients(a0, a1, a2, b0, b1, b2)
    }

    private fun calculateLowShelf(gainDb: Float, freq: Float): Coefficients {
        val fs = 22050.0
        val A = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * freq / fs
        val alpha = sin(w0) / 2.0 * sqrt((A + 1/A) * (1/0.707 - 1) + 2)
        val cosW = cos(w0)
        
        val b0 = A * ((A + 1) - (A - 1) * cosW + 2 * sqrt(A) * alpha)
        val b1 = 2 * A * ((A - 1) - (A + 1) * cosW)
        val b2 = A * ((A + 1) - (A - 1) * cosW - 2 * sqrt(A) * alpha)
        val a0 = (A + 1) + (A - 1) * cosW + 2 * sqrt(A) * alpha
        val a1 = -2 * ((A - 1) + (A + 1) * cosW)
        val a2 = (A + 1) + (A - 1) * cosW - 2 * sqrt(A) * alpha
        
        return Coefficients(a0, a1, a2, b0, b1, b2)
    }

    private fun calculatePeaking(gainDb: Float, freq: Float, q: Float): Coefficients {
        val fs = 22050.0
        val A = 10.0.pow(gainDb / 40.0)
        val w0 = 2 * PI * freq / fs
        val alpha = sin(w0) / (2 * q)
        val cosW = cos(w0)
        
        val b0 = 1 + alpha * A
        val b1 = -2 * cosW
        val b2 = 1 - alpha * A
        val a0 = 1 + alpha / A
        val a1 = -2 * cosW
        val a2 = 1 - alpha / A
        
        return Coefficients(a0, a1, a2, b0, b1, b2)
    }
}
