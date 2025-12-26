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
    private val dialogueRegex = Regex("""“([^”]*)”|«([^»]*)»|(—\s*[A-ZÁÉÍÓÚÑ][^—\n]*(?=—|\n|$))|\*([^*]+)\*|"([^"]*)"|'([^']*)'""")

    /**
     * Analyzes a raw string, sanitizes it, and splits it into a list of TextSegments.
     */
    fun analyze(rawText: String): List<TextSegment> {
        val text = TextSanitizer.sanitize(rawText)
        val segments = mutableListOf<TextSegment>()
        var lastIndex = 0

        dialogueRegex.findAll(text).forEach { matchResult ->
            val before = text.substring(lastIndex, matchResult.range.first)
            if (before.isNotBlank()) {
                segments.addAll(splitIntoParagraphSegments(before))
            }

            val content = matchResult.value
            val isThought = matchResult.groups[4] != null || matchResult.groups[6] != null

            if (isThought) {
                segments.add(NarrationSegment(
                    text = content, 
                    style = com.example.cititor.domain.model.NarrationStyle.THOUGHT
                ))
            } else {
                segments.add(DialogueSegment(text = content))
            }

            lastIndex = matchResult.range.last + 1
        }

        if (lastIndex < text.length) {
            val remainingNarration = text.substring(lastIndex)
            if (remainingNarration.isNotBlank()) {
                segments.addAll(splitIntoParagraphSegments(remainingNarration))
            }
        }

        return segments
    }

    private fun splitIntoParagraphSegments(text: String): List<NarrationSegment> {
        if (text.isEmpty()) return emptyList()
        
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
        val romanRegex = Regex("""^(?i)M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})[\.\)]?$""")
        val digitRegex = Regex("""^\d+[\.\)]?$""")
        return trimmed.isNotEmpty() && (romanRegex.matches(trimmed) || digitRegex.matches(trimmed))
    }
}
