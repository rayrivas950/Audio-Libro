package com.example.cititor.domain.analyzer

import com.example.cititor.domain.model.DialogueSegment
import com.example.cititor.domain.model.NarrationSegment
import com.example.cititor.domain.model.ProsodyIntention
import com.example.cititor.domain.model.TextSegment
import com.example.cititor.domain.sanitizer.TextSanitizer

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analyzes a given text to structure it into segments (e.g., narration, dialogue).
 * This is the core of the content analysis engine.
 */
@Singleton
class TextAnalyzer @Inject constructor(
    private val textSanitizer: TextSanitizer,
    private val dialogueResolver: DialogueResolver,
    private val intentionAnalyzer: IntentionAnalyzer,
    private val consistencyAuditor: ConsistencyAuditor
) {

    // Regex to find common dialogue markers and thoughts (marked with * or ').
    private val dialogueRegex = Regex("""“([^”]*)”|«([^»]*)»|(—\s*[A-ZÁÉÍÓÚÑ][^—\n]*(?=—|\n|$))|\*([^*]+)\*|"([^"]*)"|'([^']*)'""")

    /**
     * Analyzes a raw string, sanitizes it, and splits it into a list of TextSegments.
     */
    fun analyze(rawText: String): List<TextSegment> {
        val sanitized = textSanitizer.sanitize(rawText)
        val audited = consistencyAuditor.auditAndRepair(sanitized)
        
        return splitByStructure(audited)
    }

    private fun splitByStructure(text: String): List<TextSegment> {
        if (text.isEmpty()) return emptyList()
        
        val segments = mutableListOf<TextSegment>()
        // Split by 2 or more newlines (structural protection)
        val paragraphs = text.split(Regex("\\n{2,}"))

        paragraphs.forEach { p ->
            val trimmed = p.trim()
            if (trimmed.isBlank()) return@forEach
            
            val hasTitleMarker = trimmed.contains("[GEOMETRIC_TITLE]")
            val cleanText = trimmed.replace("[GEOMETRIC_TITLE]", "").trim()
            
            // ANALYZER DEFENSE: Even if the extractor says it's a title, 
            // the analyzer double-checks if it looks like narration or dialogue.
            val isDialogue = cleanText.startsWith("—") || cleanText.startsWith("-")
            val significantWords = countSignificantWords(cleanText)
            
            // A title should not be a dialogue and should be short (allowing 4 words for flexibility)
            val isVerifiedTitle = hasTitleMarker && !isDialogue && significantWords <= 4
            
            if (isVerifiedTitle) {
                segments.add(NarrationSegment(
                    text = cleanText,
                    intention = ProsodyIntention.NEUTRAL,
                    style = com.example.cititor.domain.model.NarrationStyle.CHAPTER_INDICATOR
                ))
            } else {
                segments.add(NarrationSegment(
                    text = cleanText,
                    intention = ProsodyIntention.NEUTRAL,
                    style = com.example.cititor.domain.model.NarrationStyle.NEUTRAL
                ))
            }
        }
        return segments
    }

    private val connectors = setOf(
        "y", "e", "o", "u", "el", "la", "los", "las", "un", "una", "unos", "unas",
        "de", "del", "a", "al", "en", "por", "para", "con", "sin", "ante", "tras",
        "mi", "tu", "su", "sus", "que"
    )

    private fun countSignificantWords(text: String): Int {
        if (text.isBlank()) return 0
        val words = text.lowercase()
            .replace(Regex("[¡!¿?,.;:()\"]"), "")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
        
        return words.count { it !in connectors }
    }

    private fun splitIntoParagraphSegments(text: String): List<NarrationSegment> {
        if (text.isEmpty()) return emptyList()
        
        val segments = mutableListOf<NarrationSegment>()
        val paragraphs = text.split("\n\n")

        paragraphs.forEachIndexed { index, p ->
            if (p.isBlank()) return@forEachIndexed
            
            // For each paragraph, we analyze its "ambient intention"
            val paragraphIntention = intentionAnalyzer.identifyIntention(p)
            
            // Split paragraph into sentences to allow mid-paragraph variations
            // This regex splits by . ! ? followed by space and capital letter (Smart split)
            val sentences = p.split(Regex("""(?<=[.!?])\s+(?=[A-ZÁÉÍÓÚÑ])"""))
            
            sentences.forEachIndexed { sIndex, sentence ->
                val fullSentence = if (sIndex < sentences.size - 1) "$sentence " else sentence
                
                // Inherit paragraph intention but allow sentence-level override
                val sentenceIntention = intentionAnalyzer.identifyIntention(sentence)
                val finalIntention = if (sentenceIntention != ProsodyIntention.NEUTRAL) {
                    sentenceIntention 
                } else {
                    paragraphIntention
                }

                if (isSectionIndicator(sentence)) {
                    segments.add(NarrationSegment(
                        text = fullSentence, 
                        intention = finalIntention, 
                        style = com.example.cititor.domain.model.NarrationStyle.CHAPTER_INDICATOR
                    ))
                } else {
                    // Logic for Breathing Segmentation (Long Sentences > 20 words)
                    val words = fullSentence.split(" ")
                    if (words.size > 20) {
                        // Attempt to split by logical connectors
                        val splitParts = splitByBreath(fullSentence)
                        splitParts.forEach { part -> 
                            segments.add(NarrationSegment(
                                text = part,
                                intention = finalIntention,
                                style = com.example.cititor.domain.model.NarrationStyle.NEUTRAL
                            ))
                        }
                    } else {
                        segments.add(NarrationSegment(
                            text = fullSentence, 
                            intention = finalIntention, 
                            style = com.example.cititor.domain.model.NarrationStyle.NEUTRAL
                        ))
                    }
                }
            }
            
            // Add paragraph break suffix to the last segment of the paragraph
            if (index < paragraphs.size - 1 || text.endsWith("\n\n")) {
                val last = segments.lastOrNull()
                if (last is NarrationSegment) {
                    segments[segments.size - 1] = last.copy(text = last.text + "\n\n")
                }
            }
        }
        return segments
    }

    private fun isSectionIndicator(text: String): Boolean {
        val trimmed = text.trim()
        val romanRegex = Regex("""^(?i)M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})[\.\)]?$""")
        val digitRegex = Regex("""^\d+[\.\)]?$""")
        return trimmed.isNotEmpty() && (romanRegex.matches(trimmed) || digitRegex.matches(trimmed))
    }

    private fun splitByBreath(text: String): List<String> {
        // Connectors that serve as good breathing points
        // We look for them surrounded by spaces to avoid splitting words
        val connectors = listOf(" y ", " e ", " ni ", " pero ", " que ", " and ", " but ", " that ")
        
        var currentText = text
        val result = mutableListOf<String>()
        
        while (currentText.split(" ").size > 15) {
            var splitIndex = -1
            var bestConnector = ""
            
            // Find the first available connector near the middle to balance the split
            for (connector in connectors) {
                val index = currentText.indexOf(connector)
                if (index != -1 && index > 10 && index < currentText.length - 10) {
                    splitIndex = index
                    bestConnector = connector
                    break // Priority to earlier connectors logic
                }
            }
            
            if (splitIndex != -1) {
                // Split inclusive of the connector for natural flow
                // Part 1: "..... y"
                // Part 2: " ....."
                val part1 = currentText.substring(0, splitIndex + bestConnector.length).trim()
                val part2 = currentText.substring(splitIndex + bestConnector.length).trim()
                
                result.add(part1)
                currentText = part2
            } else {
                break // No valid connector found to split further
            }
        }
        
        if (currentText.isNotBlank()) {
            result.add(currentText)
        }
        
        return result
    }
}
