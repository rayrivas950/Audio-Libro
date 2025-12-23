package com.example.cititor.domain.analyzer.emotion

import com.example.cititor.domain.model.Emotion

data class KeywordRule(val word: String, val emotion: Emotion, val intensity: Float = 1.0f)

interface EmotionRules {
    val keywords: List<KeywordRule>
    val adverbs: Map<String, Emotion> // Maps adverb suffix or full word to emotion modifier
    
    fun getEmotionFromPunctuation(text: String): Pair<Emotion, Float>
}
