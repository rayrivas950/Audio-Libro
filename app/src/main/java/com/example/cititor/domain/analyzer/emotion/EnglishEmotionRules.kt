package com.example.cititor.domain.analyzer.emotion

import com.example.cititor.domain.model.Emotion

class EnglishEmotionRules : EmotionRules {
    override val keywords = listOf(
        // Anger
        KeywordRule("shouted", Emotion.ANGER, 0.8f),
        KeywordRule("yelled", Emotion.ANGER, 0.9f),
        KeywordRule("roared", Emotion.ANGER, 1.0f),
        KeywordRule("snapped", Emotion.ANGER, 0.7f),
        KeywordRule("furious", Emotion.ANGER, 1.0f),
        KeywordRule("angry", Emotion.ANGER, 0.8f),
        KeywordRule("rage", Emotion.ANGER, 0.9f),
        KeywordRule("annoyed", Emotion.ANGER, 0.4f),
        
        // Joy
        KeywordRule("laughed", Emotion.JOY, 0.8f),
        KeywordRule("smiled", Emotion.JOY, 0.6f),
        KeywordRule("chuckled", Emotion.JOY, 0.7f),
        KeywordRule("happy", Emotion.JOY, 0.8f),
        KeywordRule("cheerful", Emotion.JOY, 0.7f),
        KeywordRule("excited", Emotion.JOY, 0.8f),
        
        // Sadness
        KeywordRule("cried", Emotion.SADNESS, 1.0f),
        KeywordRule("sobbed", Emotion.SADNESS, 1.0f),
        KeywordRule("wept", Emotion.SADNESS, 1.0f),
        KeywordRule("sad", Emotion.SADNESS, 0.8f),
        KeywordRule("tears", Emotion.SADNESS, 0.9f),
        KeywordRule("grief", Emotion.SADNESS, 0.9f),
        
        // Whisper
        KeywordRule("whispered", Emotion.WHISPER, 1.0f),
        KeywordRule("muttered", Emotion.WHISPER, 0.8f),
        KeywordRule("murmured", Emotion.WHISPER, 0.8f),
        KeywordRule("softly", Emotion.WHISPER, 0.7f),
        
        // Fear
        KeywordRule("trembled", Emotion.FEAR, 0.8f),
        KeywordRule("scared", Emotion.FEAR, 0.9f),
        KeywordRule("fear", Emotion.FEAR, 0.9f),
        KeywordRule("panic", Emotion.FEAR, 1.0f),
        KeywordRule("terrified", Emotion.FEAR, 1.0f),

        // Mystery
        KeywordRule("mysterious", Emotion.MYSTERY, 0.8f),
        KeywordRule("enigmatic", Emotion.MYSTERY, 0.9f),
        KeywordRule("strange", Emotion.MYSTERY, 0.5f),
        KeywordRule("darkness", Emotion.MYSTERY, 0.6f),

        // Sarcasm
        KeywordRule("sneered", Emotion.SARCASM, 0.9f),
        KeywordRule("ironic", Emotion.SARCASM, 1.0f),
        KeywordRule("sarcastic", Emotion.SARCASM, 1.0f),
        KeywordRule("mocking", Emotion.SARCASM, 0.8f),

        // Pride
        KeywordRule("proud", Emotion.PRIDE, 0.8f),
        KeywordRule("arrogant", Emotion.PRIDE, 1.0f),
        KeywordRule("haughty", Emotion.PRIDE, 0.9f),
        KeywordRule("triumphant", Emotion.PRIDE, 0.7f),

        // Disgust
        KeywordRule("disgust", Emotion.DISGUST, 1.0f),
        KeywordRule("contempt", Emotion.DISGUST, 0.9f),
        KeywordRule("revolting", Emotion.DISGUST, 1.0f),
        KeywordRule("disgusted", Emotion.DISGUST, 0.8f),

        // Exhaustion
        KeywordRule("exhausted", Emotion.EXHAUSTION, 1.0f),
        KeywordRule("tired", Emotion.EXHAUSTION, 0.8f),
        KeywordRule("fatigue", Emotion.EXHAUSTION, 0.9f),
        KeywordRule("panted", Emotion.EXHAUSTION, 0.7f),

        // Confusion
        KeywordRule("confused", Emotion.CONFUSION, 0.8f),
        KeywordRule("doubt", Emotion.CONFUSION, 0.7f),
        KeywordRule("puzzled", Emotion.CONFUSION, 0.9f),
        KeywordRule("bewildered", Emotion.CONFUSION, 0.9f),

        // Tenderness
        KeywordRule("tender", Emotion.TENDERNESS, 0.8f),
        KeywordRule("sweet", Emotion.TENDERNESS, 0.7f),
        KeywordRule("affection", Emotion.TENDERNESS, 0.9f),
        KeywordRule("love", Emotion.TENDERNESS, 0.8f)
    )

    override val adverbs = mapOf(
        "sadly" to Emotion.SADNESS,
        "happily" to Emotion.JOY,
        "angrily" to Emotion.ANGER,
        "shyly" to Emotion.FEAR,
        "softly" to Emotion.WHISPER,
        "sharply" to Emotion.ANGER
    )

    override fun getEmotionFromPunctuation(text: String): Pair<Emotion, Float> {
        return when {
            text.contains("?!") || text.contains("!?") -> Pair(Emotion.SURPRISE, 1.0f)
            text.contains("!") && text == text.uppercase() -> Pair(Emotion.ANGER, 1.0f)
            text.contains("!") -> Pair(Emotion.SURPRISE, 0.5f)
            text.contains("...") -> Pair(Emotion.SADNESS, 0.3f)
            else -> Pair(Emotion.NEUTRAL, 0.0f)
        }
    }
}
