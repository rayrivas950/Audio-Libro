package com.example.cititor.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a segment of text within a page, categorized for advanced processing (e.g., TTS).
 * Annotated with @Serializable to allow polymorphism when serializing a list of segments.
 */
@Serializable
sealed interface TextSegment {
    val text: String
    val ttsParams: TTSParameters?
}

/**
 * A segment of text representing narration.
 */
@Serializable
@SerialName("narration")
data class NarrationSegment(
    override val text: String,
    override val ttsParams: TTSParameters? = null,
    val style: NarrationStyle = NarrationStyle.NEUTRAL
) : TextSegment

/**
 * A segment of text representing dialogue.
 */
@Serializable
@SerialName("dialogue")
data class DialogueSegment(
    override val text: String,
    override val ttsParams: TTSParameters? = null,
    val speakerId: String? = null,
    val emotion: Emotion = Emotion.NEUTRAL,
    val intensity: Float = 1.0f
) : TextSegment
