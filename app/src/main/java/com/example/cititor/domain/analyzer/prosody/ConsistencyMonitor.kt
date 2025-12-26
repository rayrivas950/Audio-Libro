package com.example.cititor.domain.analyzer.prosody

import android.util.Log

/**
 * Monitors and smooths audio parameters (speed and pitch) to ensure narrative consistency.
 * Uses a moving average and a tolerance window (4-8%) to prevent abrupt jumps.
 */
class ConsistencyMonitor(
    private val windowSize: Int = 3,
    private val maxDeviation: Float = 0.08f // 8% max deviation from average
) {
    private val speedHistory = mutableListOf<Float>()
    private val pitchHistory = mutableListOf<Float>()

    /**
     * Validates and adjusts the target parameters based on history.
     * @return Pair of (adjustedSpeed, adjustedPitch)
     */
    fun validateAndAdjust(targetSpeed: Float, targetPitch: Float): Pair<Float, Float> {
        val adjustedSpeed = adjustParameter(targetSpeed, speedHistory)
        val adjustedPitch = adjustParameter(targetPitch, pitchHistory)

        // Update history
        updateHistory(adjustedSpeed, speedHistory)
        updateHistory(adjustedPitch, pitchHistory)

        return adjustedSpeed to adjustedPitch
    }

    private fun adjustParameter(target: Float, history: MutableList<Float>): Float {
        if (history.isEmpty()) return target

        val average = history.average().toFloat()
        val minAllowed = average * (1.0f - maxDeviation)
        val maxAllowed = average * (1.0f + maxDeviation)

        val adjusted = target.coerceIn(minAllowed, maxAllowed)
        
        if (adjusted != target) {
            // Log.d("ConsistencyMonitor", "Parameter adjusted: $target -> $adjusted (Avg: $average, Range: $minAllowed..$maxAllowed)")
        }

        return adjusted
    }

    private fun updateHistory(value: Float, history: MutableList<Float>) {
        history.add(value)
        if (history.size > windowSize) {
            history.removeAt(0)
        }
    }

    /**
     * Resets the monitor for a new session.
     */
    fun reset() {
        speedHistory.clear()
        pitchHistory.clear()
    }
}
