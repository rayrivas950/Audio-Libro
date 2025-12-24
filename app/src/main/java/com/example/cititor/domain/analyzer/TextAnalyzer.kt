package com.example.cititor.domain.analyzer

import com.example.cititor.domain.model.DialogueSegment
import com.example.cititor.domain.model.NarrationSegment
import com.example.cititor.domain.model.TextSegment
import com.example.cititor.domain.sanitizer.TextSanitizer

/**
 * Analyzes a given text to structure it into segments (e.g., narration, dialogue).
 * This is the core of the content analysis engine.
 */
object TextAnalyzer {

    // Regex to find common dialogue markers (double quotes, guillemets, em dashes).
    // It captures the content *inside* the markers.
    // Regex to find common dialogue markers and thoughts (marked with *).
    // It captures the content *inside* the markers.
    private val dialogueRegex = Regex("“([^”]*)”|«([^»]*)»|—([^—]*)—|\\*([^*]+)\\*|\"([^\"]*)\"")

    /**
     * Analyzes a raw string, sanitizes it, and splits it into a list of TextSegments.
     * Returns the segments and the list of characters detected in this chunk.
     *
     * @param rawText The raw text from a book page.
     * @param characterRegistry An optional registry to maintain character consistency across pages.
     * @return A Pair containing the list of [TextSegment] and a list of [Character] detected.
     */
    fun analyze(
        rawText: String, 
        characterRegistry: com.example.cititor.domain.analyzer.character.CharacterRegistry = com.example.cititor.domain.analyzer.character.CharacterRegistry()
    ): Pair<List<TextSegment>, List<com.example.cititor.domain.model.Character>> {
        val text = TextSanitizer.sanitize(rawText)
        val rawSegments = mutableListOf<TextSegment>()
        var lastIndex = 0

        // Phase 1: Segmentation
        dialogueRegex.findAll(text).forEach { matchResult ->
            val narrationText = text.substring(lastIndex, matchResult.range.first).trim()
            if (narrationText.isNotEmpty()) {
                rawSegments.add(NarrationSegment(text = narrationText))
            }

            // Groups: 1=Quotes, 2=Guillemets, 3=EmDash, 4=Asterisks (Thought), 5=Standard Quotes
            val dialogueText = (matchResult.groups[1] ?: matchResult.groups[2] ?: matchResult.groups[3] ?: matchResult.groups[5])?.value?.trim()
            val thoughtText = matchResult.groups[4]?.value?.trim()

            if (thoughtText != null) {
                rawSegments.add(NarrationSegment(text = thoughtText, style = com.example.cititor.domain.model.NarrationStyle.THOUGHT))
            } else if (!dialogueText.isNullOrEmpty()) {
                rawSegments.add(DialogueSegment(text = dialogueText))
            }

            lastIndex = matchResult.range.last + 1
        }

        if (lastIndex < text.length) {
            val remainingNarration = text.substring(lastIndex).trim()
            if (remainingNarration.isNotEmpty()) {
                rawSegments.add(NarrationSegment(text = remainingNarration))
            }
        }

        // Phase 2: Enrichment (Emotion & Character Detection)
        val language = detectLanguage(text)
        return enrichSegments(rawSegments, language, characterRegistry)
    }

    private fun detectLanguage(text: String): String {
        val sample = text.take(500).lowercase()
        return if (sample.contains(" the ") || sample.contains(" and ") || sample.contains(" is ")) {
            "en"
        } else {
            "es"
        }
    }

    private fun enrichSegments(
        segments: List<TextSegment>, 
        language: String,
        characterRegistry: com.example.cititor.domain.analyzer.character.CharacterRegistry
    ): Pair<List<TextSegment>, List<com.example.cititor.domain.model.Character>> {
        val characterDetector = com.example.cititor.domain.analyzer.character.CharacterDetector()

        val enrichedSegments = segments.mapIndexed { index, segment ->
            if (segment is DialogueSegment) {
                // Look at context before and after
                val prevSegment = segments.getOrNull(index - 1) as? NarrationSegment
                val nextSegment = segments.getOrNull(index + 1) as? NarrationSegment
                
                val contextText = (prevSegment?.text ?: "") + " " + (nextSegment?.text ?: "")
                
                // 1. Emotion Detection
                val (emotion, intensity) = com.example.cititor.domain.analyzer.emotion.EmotionDetector.detect(
                    dialogueText = segment.text,
                    contextText = contextText,
                    languageCode = language
                )

                // 2. Character Detection
                val charGuess = characterDetector.detectSpeaker(contextText, language)
                var speakerId: String? = null
                
                if (charGuess != null) {
                    val character = characterRegistry.getOrCreate(charGuess.name, charGuess.gender)
                    speakerId = character.id
                }

                segment.copy(
                    emotion = emotion, 
                    intensity = intensity,
                    speakerId = speakerId
                )
            } else {
                segment
            }
        }
        
        return Pair(enrichedSegments, characterRegistry.getAll())
    }
}
