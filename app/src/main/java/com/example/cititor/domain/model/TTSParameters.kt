package com.example.cititor.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class TTSParameters(
    val pitch: Float? = null,      // 1.0 = normal
    val speed: Float? = null,      // 1.0 = normal
    val volume: Float? = null,     // 1.0 = normal
    val emphasis: Boolean = false,
    val pausePost: Long? = null    // ms
)
