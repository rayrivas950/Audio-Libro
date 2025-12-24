package com.example.cititor.domain.model

object VoiceProfiles {
    
    val DEFAULT = VoiceProfile(
        id = "default",
        name = "Normal",
        pitch = 1.0f,
        speed = 1.0f
    )

    val HERO_MALE = VoiceProfile(
        id = "hero_male",
        name = "Héroe",
        pitch = 0.9f, // Slightly deeper
        speed = 1.0f
    )

    val HERO_FEMALE = VoiceProfile(
        id = "hero_female",
        name = "Heroína",
        pitch = 1.1f, // Slightly higher/clearer
        speed = 1.0f
    )

    val VILLAIN = VoiceProfile(
        id = "villain",
        name = "Villano",
        pitch = 0.8f, // Deep and ominous
        speed = 0.9f // Slightly slower
    )

    val ELDERLY = VoiceProfile(
        id = "elderly",
        name = "Anciano",
        pitch = 0.7f, // Lower, maybe shaky in future
        speed = 0.8f // Slower
    )

    val CHILD = VoiceProfile(
        id = "child",
        name = "Niño",
        pitch = 1.4f, // High pitch
        speed = 1.1f // Faster
    )

    val GIANT = VoiceProfile(
        id = "giant",
        name = "Gigante",
        pitch = 0.5f, // Very deep
        speed = 0.7f // Very slow
    )

    val MYSTERIOUS = VoiceProfile(
        id = "mysterious",
        name = "Misterioso",
        pitch = 0.9f,
        speed = 0.9f
        // In future: add breathy tone param
    )
    
    val NARRATOR = VoiceProfile(
        id = "narrator",
        name = "Narrador",
        pitch = 1.0f,
        speed = 1.0f
    )
    
    // Helper to get all standard profiles
    fun getAll() = listOf(
        DEFAULT, HERO_MALE, HERO_FEMALE, VILLAIN, ELDERLY, CHILD, GIANT, MYSTERIOUS, NARRATOR
    )
    
    fun getById(id: String): VoiceProfile {
        return getAll().find { it.id == id } ?: DEFAULT
    }
}
