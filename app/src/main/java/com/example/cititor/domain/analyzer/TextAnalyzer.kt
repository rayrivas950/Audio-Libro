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
        val segments = mutableListOf<TextSegment>()
        var lastIndex = 0

        dialogueRegex.findAll(text).forEach { matchResult ->
            // 1. Add any text before the dialogue as a NarrationSegment.
            val narrationText = text.substring(lastIndex, matchResult.range.first).trim()
            if (narrationText.isNotEmpty()) {
                segments.add(NarrationSegment(text = narrationText))
            }

            // 2. Add the dialogue itself as a DialogueSegment.
            // The content is in one of the capturing groups.
            val dialogueText = (matchResult.groups[1] ?: matchResult.groups[2] ?: matchResult.groups[3])?.value?.trim()
            if (!dialogueText.isNullOrEmpty()) {
                segments.add(DialogueSegment(text = dialogueText))
            }

            // 3. Update the index to the end of the current match.
            lastIndex = matchResult.range.last + 1
        }

        // 4. Add any remaining text after the last dialogue as a final NarrationSegment.
        if (lastIndex < text.length) {
            val remainingNarration = text.substring(lastIndex).trim()
            if (remainingNarration.isNotEmpty()) {
                segments.add(NarrationSegment(text = remainingNarration))
            }
        }

        return segments
    }
}
