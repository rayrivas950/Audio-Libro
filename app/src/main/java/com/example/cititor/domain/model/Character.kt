package com.example.cititor.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Character(
    val id: String,
    val name: String,
    val aliases: Set<String> = emptySet(),
    val isProtagonist: Boolean = false,
    val gender: Gender = Gender.UNKNOWN,
    val ageRange: AgeRange = AgeRange.ADULT,
    val voiceProfile: String? = null
)

@Serializable
enum class Gender { MALE, FEMALE, UNKNOWN }

@Serializable
enum class AgeRange { CHILD, YOUNG, ADULT, ELDERLY }
