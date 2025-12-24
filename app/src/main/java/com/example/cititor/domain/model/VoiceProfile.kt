package com.example.cititor.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class VoiceProfile(
    val id: String,
    val name: String,
    val pitch: Float = 1.0f, // 1.0 is normal
    val speed: Float = 1.0f, // 1.0 is normal
    val engineVoiceId: String? = null // Specific TTS engine voice ID if needed
)
