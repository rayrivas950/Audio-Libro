package com.example.cititor.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents a segment of text within a page, categorized for advanced processing (e.g., TTS).
 * Annotated with @Serializable to allow polymorphism when serializing a list of segments.
 */
@Serializable
sealed interface TextSegment {
    val text: String
}

/**
 * A segment of text representing narration.
 */
@Serializable
data class NarrationSegment(override val text: String) : TextSegment

/**
 * A segment of text representing dialogue.
 * In the future, this can be expanded to include a characterId.
 */
@Serializable
data class DialogueSegment(override val text: String) : TextSegment
