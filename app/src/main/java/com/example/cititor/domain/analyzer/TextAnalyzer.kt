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

    // Regex to find common dialogue markers and thoughts (marked with * or ').
    // Improved to handle em-dashes more robustly in Spanish literature.
    // It matches dialogues starting with uppercase after the dash and uses lookahead for the closing dash.
    // Groups: 1=Quotes, 2=Guillemets, 3=EmDash, 4=Asterisks (Thought), 5=Standard Quotes, 6=Single Quotes (Thought)
    private val dialogueRegex = Regex("""“([^”]*)”|«([^»]*)»|(—\s*[A-ZÁÉÍÓÚÑ][^—\n]*(?=—|\n|$))|\*([^*]+)\*|"([^"]*)"|'([^']*)'""")

    /**
     * Analyzes a raw string, sanitizes it, and splits it into a list of TextSegments.
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
            val before = text.substring(lastIndex, matchResult.range.first)
            if (before.isNotBlank()) {
                splitIntoParagraphSegments(before).forEach { rawSegments.add(it) }
            }

            val content = matchResult.value
            val thoughtText = matchResult.groups[4]?.value?.trim() ?: matchResult.groups[6]?.value?.trim()

            if (thoughtText != null) {
                rawSegments.add(NarrationSegment(
                    text = content, 
                    style = com.example.cititor.domain.model.NarrationStyle.THOUGHT
                ))
            } else {
                // Simplified dialogue segments for factory reset
                rawSegments.add(DialogueSegment(text = content))
            }

            lastIndex = matchResult.range.last + 1
        }

        if (lastIndex < text.length) {
            val remainingNarration = text.substring(lastIndex)
            if (remainingNarration.isNotBlank()) {
                splitIntoParagraphSegments(remainingNarration).forEach { rawSegments.add(it) }
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
                
                // 1. Emotion Detection (Still kept for UI markers, though TTS is currently neutralized)
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

    private fun splitIntoParagraphSegments(text: String): List<NarrationSegment> {
        if (text.isEmpty()) return emptyList()
        
        // Split by \n\n but keep track of them
        val paragraphs = text.split("\n\n")
        return paragraphs.mapIndexed { index, p ->
            val suffix = if (index < paragraphs.size - 1) "\n\n" else if (text.endsWith("\n\n")) "\n\n" else ""
            val fullParagraph = p + suffix
            
            if (isSectionIndicator(p)) {
                NarrationSegment(text = fullParagraph, style = com.example.cititor.domain.model.NarrationStyle.CHAPTER_INDICATOR)
            } else {
                NarrationSegment(text = fullParagraph, style = com.example.cititor.domain.model.NarrationStyle.NEUTRAL)
            }
        }.filter { it.text.isNotEmpty() }
    }

    private fun isSectionIndicator(text: String): Boolean {
        val trimmed = text.trim()
        // Roman numerals (I, V, X...) or standalone digits (1, 2, 3...)
        val romanRegex = Regex("""^(?i)M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})[\.\)]?$""")
        val digitRegex = Regex("""^\d+[\.\)]?$""")
        return trimmed.isNotEmpty() && (romanRegex.matches(trimmed) || digitRegex.matches(trimmed))
    }
}
