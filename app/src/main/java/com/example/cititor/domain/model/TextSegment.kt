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
    val speakerId: String?
    val intention: ProsodyIntention
}

/**
 * A segment of text representing narration.
 */
@Serializable
@SerialName("narration")
data class NarrationSegment(
    override val text: String,
    override val ttsParams: TTSParameters? = null,
    override val speakerId: String? = "Narrator",
    override val intention: ProsodyIntention = ProsodyIntention.NEUTRAL,
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
    override val speakerId: String? = null,
    override val intention: ProsodyIntention = ProsodyIntention.NEUTRAL
) : TextSegment

/**
 * A segment representing an illustrative image within the text.
 */
@Serializable
@SerialName("image")
data class ImageSegment(
    val imagePath: String,
    val caption: String? = null,
    // Interface overrides to satisfy TextSegment (Image is silent or reads caption)
    override val text: String = caption ?: "", 
    override val ttsParams: TTSParameters? = null,
    override val speakerId: String? = null,
    override val intention: ProsodyIntention = ProsodyIntention.NEUTRAL
) : TextSegment
