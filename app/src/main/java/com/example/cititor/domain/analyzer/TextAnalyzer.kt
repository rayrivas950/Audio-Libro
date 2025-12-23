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
    private val dialogueRegex = Regex("“([^”]*)”|«([^»]*)»|—([^—]*)—")

    /**
     * Analyzes a raw string, sanitizes it, and splits it into a list of TextSegments.
     *
     * @param rawText The raw text from a book page, potentially with HTML and artifacts.
     * @return A list of [TextSegment] objects, ordered as they appear in the text.
     */
    fun analyze(rawText: String): List<TextSegment> {
        val text = TextSanitizer.sanitize(rawText)
        val rawSegments = mutableListOf<TextSegment>()
        var lastIndex = 0

        // Phase 1: Segmentation
        dialogueRegex.findAll(text).forEach { matchResult ->
            // 1. Add any text before the dialogue as a NarrationSegment.
            val narrationText = text.substring(lastIndex, matchResult.range.first).trim()
            if (narrationText.isNotEmpty()) {
                rawSegments.add(NarrationSegment(text = narrationText))
            }

            // 2. Add the dialogue itself as a DialogueSegment.
            // The content is in one of the capturing groups.
            val dialogueText = (matchResult.groups[1] ?: matchResult.groups[2] ?: matchResult.groups[3])?.value?.trim()
            if (!dialogueText.isNullOrEmpty()) {
                rawSegments.add(DialogueSegment(text = dialogueText))
            }

            // 3. Update the index to the end of the current match.
            lastIndex = matchResult.range.last + 1
        }

        // 4. Add any remaining text after the last dialogue as a final NarrationSegment.
        if (lastIndex < text.length) {
            val remainingNarration = text.substring(lastIndex).trim()
            if (remainingNarration.isNotEmpty()) {
                rawSegments.add(NarrationSegment(text = remainingNarration))
            }
        }

        // Phase 2: Enrichment (Emotion Detection)
        val language = detectLanguage(text)
        return enrichSegments(rawSegments, language)
    }

    private fun detectLanguage(text: String): String {
        // Very basic heuristic for now
        val sample = text.take(500).lowercase()
        return if (sample.contains(" the ") || sample.contains(" and ") || sample.contains(" is ")) {
            "en"
        } else {
            "es" // Default to Spanish
        }
    }

    private fun enrichSegments(segments: List<TextSegment>, language: String): List<TextSegment> {
        return segments.mapIndexed { index, segment ->
            if (segment is DialogueSegment) {
                // Look at context before and after
                val prevSegment = segments.getOrNull(index - 1) as? NarrationSegment
                val nextSegment = segments.getOrNull(index + 1) as? NarrationSegment
                
                val contextText = (prevSegment?.text ?: "") + " " + (nextSegment?.text ?: "")
                
                val (emotion, intensity) = com.example.cititor.domain.analyzer.emotion.EmotionDetector.detect(
                    dialogueText = segment.text,
                    contextText = contextText,
                    languageCode = language
                )

                segment.copy(emotion = emotion, intensity = intensity)
            } else {
                segment
            }
        }
    }
}
