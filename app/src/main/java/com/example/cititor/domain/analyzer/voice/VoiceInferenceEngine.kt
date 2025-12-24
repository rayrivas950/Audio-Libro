package com.example.cititor.domain.analyzer.voice

import com.example.cititor.domain.model.AgeRange
import com.example.cititor.domain.model.Character
import com.example.cititor.domain.model.Gender
import com.example.cititor.domain.model.VoiceProfile
import com.example.cititor.domain.model.VoiceProfiles
import java.util.Locale

class VoiceInferenceEngine {

    fun inferProfile(character: Character, introductionText: String? = null): VoiceProfile {
        // 1. Check for Mysterious/Unknown characters first
        if (isMysterious(character)) {
            return VoiceProfiles.MYSTERIOUS
        }

        // 2. Analyze physical traits from introduction text (if available)
        val traits = if (introductionText != null) extractPhysicalTraits(introductionText) else emptySet()
        
        // 3. Determine base profile based on Gender and Age
        var baseProfile = getBaseProfile(character.gender, character.ageRange)

        // 4. Adjust based on traits (Giant, Tiny, etc.)
        if (traits.contains("giant")) {
            return VoiceProfiles.GIANT
        }
        
        // If no specific trait overrides, return the base profile
        return baseProfile
    }

    private fun isMysterious(character: Character): Boolean {
        val nameLower = character.name.lowercase(Locale.getDefault())
        val aliasesLower = character.aliases.map { it.lowercase(Locale.getDefault()) }
        
        val mysteriousKeywords = setOf(
            "sombra", "shadow", 
            "encapuchado", "hooded", 
            "desconocido", "unknown", 
            "extraño", "stranger",
            "voz", "voice" // e.g. "Una voz dijo"
        )

        if (mysteriousKeywords.any { nameLower.contains(it) }) return true
        if (aliasesLower.any { alias -> mysteriousKeywords.any { alias.contains(it) } }) return true
        
        return false
    }

    private fun getBaseProfile(gender: Gender, ageRange: AgeRange): VoiceProfile {
        return when (ageRange) {
            AgeRange.CHILD -> VoiceProfiles.CHILD
            AgeRange.ELDERLY -> VoiceProfiles.ELDERLY
            else -> {
                when (gender) {
                    Gender.MALE -> VoiceProfiles.HERO_MALE // Default male
                    Gender.FEMALE -> VoiceProfiles.HERO_FEMALE // Default female
                    Gender.UNKNOWN -> VoiceProfiles.DEFAULT
                }
            }
        }
    }

    private fun extractPhysicalTraits(text: String): Set<String> {
        val lowerText = text.lowercase(Locale.getDefault())
        val traits = mutableSetOf<String>()

        val giantKeywords = setOf("gigante", "giant", "enorme", "huge", "colosal", "colossal", "ogro", "ogre")
        val tinyKeywords = setOf("diminuto", "tiny", "pequeño", "small", "enano", "dwarf")

        if (giantKeywords.any { lowerText.contains(it) }) traits.add("giant")
        if (tinyKeywords.any { lowerText.contains(it) }) traits.add("tiny")

        return traits
    }
}
