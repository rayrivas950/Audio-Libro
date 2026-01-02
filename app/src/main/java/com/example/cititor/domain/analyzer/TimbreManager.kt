package com.example.cititor.domain.analyzer

import com.example.cititor.domain.model.TimbreProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Intelligent manager that assigns unique voice profiles to characters.
 * Supports up to ~50 base timbres using DSP offsets.
 */
@Singleton
class TimbreManager @Inject constructor() {

    fun generateProfile(detection: CharacterDetection): TimbreProfile {
        // Base profile selection
        val gender = detection.genderHeuristic
        val desc = detection.description?.lowercase() ?: ""
        
        var basePitch = 1.0f
        var bassBoost = 0.0f
        var trebleBoost = 0.0f

        // 1. Gender-based initialization
        when (gender) {
            "male" -> {
                basePitch = 0.88f
                bassBoost = 1.5f
            }
            "female" -> {
                basePitch = 1.12f
                trebleBoost = 1.0f
            }
        }

        // 2. Description-based refinement
        if (desc.contains("ronca") || desc.contains("grave") || desc.contains("profunda")) {
            basePitch -= 0.05f
            bassBoost += 2.0f
        }
        if (desc.contains("aguda") || desc.contains("suave")) {
            basePitch += 0.05f
            trebleBoost += 1.5f
        }

        // 3. Fallback / Uniqueness Jitter
        // Use name hash to ensure the same character always gets the same "pseudo-random" voice
        val seed = detection.name.hashCode().toLong()
        val random = Random(seed)
        
        val pitchJitter = random.nextDouble(-0.03, 0.03).toFloat()
        val eqJitter = random.nextDouble(-0.5, 0.5).toFloat()

        return TimbreProfile(
            pitchShift = basePitch + pitchJitter,
            lowGainDb = bassBoost + eqJitter,
            highGainDb = trebleBoost - eqJitter
        )
    }

    /**
     * Creates a generic neutral profile for unknown speakers.
     */
    fun getNeutralProfile(): TimbreProfile = TimbreProfile()
}
