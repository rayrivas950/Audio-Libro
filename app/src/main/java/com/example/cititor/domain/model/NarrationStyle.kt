package com.example.cititor.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class NarrationStyle(val key: String) {
    NEUTRAL("neutral"),
    DESCRIPTIVE("descriptive"),
    TENSE("tense"),
    CALM("calm"),
    MYSTERIOUS("mysterious"),
    THOUGHT("thought"),
    CHAPTER_INDICATOR("chapter_indicator");
}
