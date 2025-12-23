package com.example.cititor.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class Emotion(val key: String) {
    NEUTRAL("neutral"),
    JOY("joy"),
    SADNESS("sadness"),
    ANGER("anger"),
    FEAR("fear"),
    SURPRISE("surprise"),
    URGENCY("urgency"),
    WHISPER("whisper");
}
