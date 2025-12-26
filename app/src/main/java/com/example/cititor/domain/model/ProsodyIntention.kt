package com.example.cititor.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents the narrator's intention or emotional tone for a segment.
 * These are "semantic tags" that the ProsodyEngine will interpret.
 */
@Serializable
enum class ProsodyIntention {
    NEUTRAL,
    WHISPER,    // "susurró", "murmuró"
    SHOUT,      // "gritó", "exclamó", "!!"
    SUSPENSE,   // "...", slow down
    TENSION,    // Short sentences, faster pace
    THOUGHT,    // Internal monologue
    ADRENALINE, // High energy, quick pace (action scenes)
    SOLEMN      // Deep, slow, formal (important speeches or moments)
}
