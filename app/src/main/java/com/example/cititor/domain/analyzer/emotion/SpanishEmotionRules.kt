package com.example.cititor.domain.analyzer.emotion

import com.example.cititor.domain.model.Emotion

class SpanishEmotionRules : EmotionRules {
    override val keywords = listOf(
        // Anger
        KeywordRule("gritó", Emotion.ANGER, 0.8f),
        KeywordRule("bramó", Emotion.ANGER, 1.0f),
        KeywordRule("rugió", Emotion.ANGER, 1.0f),
        KeywordRule("espetó", Emotion.ANGER, 0.7f),
        KeywordRule("furioso", Emotion.ANGER, 1.0f),
        KeywordRule("enojado", Emotion.ANGER, 0.8f),
        KeywordRule("ira", Emotion.ANGER, 0.9f),
        KeywordRule("molesto", Emotion.ANGER, 0.4f),
        
        // Joy
        KeywordRule("rió", Emotion.JOY, 0.8f),
        KeywordRule("sonrió", Emotion.JOY, 0.6f),
        KeywordRule("carcajada", Emotion.JOY, 1.0f),
        KeywordRule("feliz", Emotion.JOY, 0.8f),
        KeywordRule("alegre", Emotion.JOY, 0.7f),
        KeywordRule("entusiasmo", Emotion.JOY, 0.6f),
        
        // Sadness
        KeywordRule("lloró", Emotion.SADNESS, 1.0f),
        KeywordRule("sollozó", Emotion.SADNESS, 1.0f),
        KeywordRule("gimió", Emotion.SADNESS, 0.8f),
        KeywordRule("triste", Emotion.SADNESS, 0.8f),
        KeywordRule("lágrimas", Emotion.SADNESS, 0.9f),
        KeywordRule("pena", Emotion.SADNESS, 0.7f),
        
        // Whisper
        KeywordRule("susurró", Emotion.WHISPER, 1.0f),
        KeywordRule("murmuró", Emotion.WHISPER, 0.8f),
        KeywordRule("musitó", Emotion.WHISPER, 0.8f),
        KeywordRule("bajo", Emotion.WHISPER, 0.5f), // Context dependent, low weight
        KeywordRule("oído", Emotion.WHISPER, 0.6f),
        
        // Fear
        KeywordRule("tembló", Emotion.FEAR, 0.8f),
        KeywordRule("miedo", Emotion.FEAR, 0.9f),
        KeywordRule("pánico", Emotion.FEAR, 1.0f),
        KeywordRule("terror", Emotion.FEAR, 1.0f),
        KeywordRule("asustado", Emotion.FEAR, 0.8f)
    )

    override val adverbs = mapOf(
        "tristemente" to Emotion.SADNESS,
        "alegremente" to Emotion.JOY,
        "furiosamente" to Emotion.ANGER,
        "tímidamente" to Emotion.FEAR,
        "suavemente" to Emotion.WHISPER,
        "bruscamente" to Emotion.ANGER
    )

    override fun getEmotionFromPunctuation(text: String): Pair<Emotion, Float> {
        return when {
            text.contains("?!") || text.contains("!?") -> Pair(Emotion.SURPRISE, 1.0f) // Could be anger too, context decides
            text.contains("!") && text == text.uppercase() -> Pair(Emotion.ANGER, 1.0f) // SHOUTING
            text.contains("!") -> Pair(Emotion.SURPRISE, 0.5f)
            text.contains("...") -> Pair(Emotion.SADNESS, 0.3f) // Hesitation/Sadness
            else -> Pair(Emotion.NEUTRAL, 0.0f)
        }
    }
}
