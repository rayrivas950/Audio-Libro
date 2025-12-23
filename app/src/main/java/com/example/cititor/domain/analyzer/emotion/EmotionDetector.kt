package com.example.cititor.domain.analyzer.emotion

import com.example.cititor.domain.model.Emotion
import java.util.Locale

object EmotionDetector {

    private val rules = mapOf(
        "es" to SpanishEmotionRules(),
        "en" to EnglishEmotionRules()
    )

    fun detect(
        dialogueText: String,
        contextText: String?,
        languageCode: String = "es"
    ): Pair<Emotion, Float> {
        val ruleSet = rules[languageCode] ?: rules["es"]!!
        val scores = mutableMapOf<Emotion, Float>()

        // 1. Context Analysis (Narrative around dialogue)
        if (contextText != null) {
            val lowerContext = contextText.lowercase(Locale.getDefault())
            
            // A. Keywords (Verbs, adjectives)
            ruleSet.keywords.forEach { rule ->
                if (lowerContext.contains(rule.word)) {
                    val currentScore = scores.getOrDefault(rule.emotion, 0f)
                    scores[rule.emotion] = currentScore + rule.intensity
                }
            }

            // B. Adverbs (-mente, -ly)
            ruleSet.adverbs.forEach { (adverb, emotion) ->
                if (lowerContext.contains(adverb)) {
                    val currentScore = scores.getOrDefault(emotion, 0f)
                    scores[emotion] = currentScore + 0.5f // Adverbs add significant weight
                }
            }
        }

        // 2. Content Analysis (Punctuation & Caps)
        val (punctuationEmotion, punctuationIntensity) = ruleSet.getEmotionFromPunctuation(dialogueText)
        if (punctuationEmotion != Emotion.NEUTRAL) {
             val currentScore = scores.getOrDefault(punctuationEmotion, 0f)
             scores[punctuationEmotion] = currentScore + punctuationIntensity
        }

        // 3. Determine Winner
        if (scores.isEmpty()) {
            return Pair(Emotion.NEUTRAL, 0.0f)
        }

        // Find the emotion with the highest score
        val winner = scores.maxByOrNull { it.value }!!
        
        // Normalize intensity to max 1.0f
        val finalIntensity = winner.value.coerceAtMost(1.0f)
        
        return Pair(winner.key, finalIntensity)
    }
}
