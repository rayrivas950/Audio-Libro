package com.example.cititor.domain.model

import kotlinx.serialization.Serializable

/**
 * Detailed instructions for the TTS and Audio pipeline.
 * Part of the advanced prosody system (Phase 35).
 */
@Serializable
data class ProsodyInstruction(
    val pausePre: Long = 0,
    val pausePost: Long = 0,
    val speedMultiplier: Float = 1.0f,
    val pitchMultiplier: Float = 1.0f,
    val volumeMultiplier: Float = 1.0f,
    val arousal: Float = 0.5f,
    val valence: Float = 0.0f,
    val dominance: Float = 0.5f
)

/**
 * DSP profile to define a unique voice timbre.
 */
@Serializable
data class TimbreProfile(
    val pitchShift: Float = 1.0f,
    val lowGainDb: Float = 0.0f,
    val midGainDb: Float = 0.0f,
    val highGainDb: Float = 0.0f
)
