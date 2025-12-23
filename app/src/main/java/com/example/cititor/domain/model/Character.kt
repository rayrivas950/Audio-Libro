package com.example.cititor.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Character(
    val id: String,
    val name: String,
    val gender: Gender = Gender.UNKNOWN,
    val ageRange: AgeRange = AgeRange.ADULT,
    val voiceProfile: String? = null // ID de voz (ej. "es-ES-Neural2-A")
)

@Serializable
enum class Gender { MALE, FEMALE, UNKNOWN }

@Serializable
enum class AgeRange { CHILD, YOUNG, ADULT, ELDERLY }
