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
            val narrationText = text.substring(lastIndex, matchResult.range.first).trim(' ', '\t')
            if (narrationText.isNotEmpty()) {
                splitIntoParagraphSegments(narrationText).forEach { rawSegments.add(it) }
            }

            // Groups: 1=Quotes, 2=Guillemets, 3=EmDash, 4=Asterisks (Thought), 5=Standard Quotes, 6=Single Quotes (Thought)
            val thoughtText = matchResult.groups[4]?.value?.trim() ?: matchResult.groups[6]?.value?.trim()
            val content = matchResult.value

            if (thoughtText != null) {
                rawSegments.add(NarrationSegment(
                    text = content, 
                    style = com.example.cititor.domain.model.NarrationStyle.THOUGHT
                ))
            } else {
                // Split long dialogues into intelligent segments for better pauses
                splitIntoIntelligentSegments(content).forEach { part ->
                    rawSegments.add(DialogueSegment(text = part))
                }
            }

            lastIndex = matchResult.range.last + 1
        }

        if (lastIndex < text.length) {
            val remainingNarration = text.substring(lastIndex).trim(' ', '\t')
            if (remainingNarration.isNotEmpty()) {
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

    private fun splitIntoParagraphSegments(text: String): List<NarrationSegment> {
        if (text.isEmpty()) return emptyList()
        
        // Split by \n\n but keep track of them
        val paragraphs = text.split("\n\n")
        return paragraphs.flatMapIndexed { index, p ->
            val suffix = if (index < paragraphs.size - 1) "\n\n" else if (text.endsWith("\n\n")) "\n\n" else ""
            val fullParagraph = p + suffix
            
            if (isSectionIndicator(p)) {
                listOf(NarrationSegment(text = fullParagraph, style = com.example.cititor.domain.model.NarrationStyle.CHAPTER_INDICATOR))
            } else {
                // Split paragraph into intelligent segments (sentences, commas, etc.)
                splitIntoIntelligentSegments(fullParagraph).map { part ->
                    NarrationSegment(text = part, style = com.example.cititor.domain.model.NarrationStyle.NEUTRAL)
                }
            }
        }.filter { it.text.isNotEmpty() }
    }

    private fun splitIntoIntelligentSegments(text: String): List<String> {
        if (text.isBlank()) return emptyList()

        // 1. Split by major punctuation: . ! ? , ; :
        // We use a regex that keeps the punctuation with the preceding text
        val punctRegex = Regex("""[^,.!?;:]+[,.!?;:]+(?:\s+|$)""")
        var segments = punctRegex.findAll(text).map { it.value }.toMutableList()
        
        val lastMatchEnd = punctRegex.findAll(text).lastOrNull()?.range?.last ?: -1
        if (lastMatchEnd < text.length - 1) {
            segments.add(text.substring(lastMatchEnd + 1))
        }
        
        if (segments.isEmpty()) segments.add(text)

        // 2. Further split long segments by conjunctions (pero, aunque, etc.)
        // Only if the segment is long enough to warrant a breath
        return segments.flatMap { segment ->
            if (segment.length > 60) {
                val conjRegex = Regex("""\s+(?=pero|aunque|sin embargo|no obstante)\s*""")
                val parts = segment.split(conjRegex).filter { it.isNotBlank() }
                if (parts.size > 1) parts else listOf(segment)
            } else {
                listOf(segment)
            }
        }.filter { it.isNotBlank() }
    }

    private fun isSectionIndicator(text: String): Boolean {
        val trimmed = text.trim()
        // Roman numerals (I, V, X...) or standalone digits (1, 2, 3...)
        // Optional trailing dot or parenthesis
        val romanRegex = Regex("""^(?i)M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})[\.\)]?$""")
        val digitRegex = Regex("""^\d+[\.\)]?$""")
        return trimmed.isNotEmpty() && (romanRegex.matches(trimmed) || digitRegex.matches(trimmed))
    }
}
