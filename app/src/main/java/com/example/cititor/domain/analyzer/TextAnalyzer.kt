package com.example.cititor.domain.analyzer

import com.example.cititor.domain.model.DialogueSegment
import com.example.cititor.domain.model.ImageSegment
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
    private val characterDetector: CharacterDetector,
    private val intentionAnalyzer: IntentionAnalyzer,
    private val consistencyAuditor: ConsistencyAuditor
) {

    // Regex to find common dialogue markers and thoughts (marked with * or ').
    private val dialogueRegex = Regex("""“([^”]*)”|«([^»]*)»|(—\s*[A-ZÁÉÍÓÚÑ][^—\n]*(?=—|\n|$))|\*([^*]+)\*|"([^"]*)"|'([^']*)'""")

    /**
     * Analyzes a raw string, sanitizes it, and splits it into a list of TextSegments.
     */
    fun analyze(rawText: String, isEpub: Boolean = false): List<TextSegment> {
        val sanitized = textSanitizer.sanitize(rawText, isEpub = isEpub)
        val audited = consistencyAuditor.auditAndRepair(sanitized)
        
        return splitByStructure(audited)
    }

    private fun splitByStructure(text: String): List<TextSegment> {
        if (text.isEmpty()) return emptyList()
        
        val segments = mutableListOf<TextSegment>()
        // Split by the Definitive Separator (or fallback to newlines)
        val paragraphs = if (text.contains(com.example.cititor.data.text_extractor.EpubExtractor.BLOCK_SEPARATOR)) {
             text.split(com.example.cititor.data.text_extractor.EpubExtractor.BLOCK_SEPARATOR)
        } else {
             text.split(Regex("\\n{2,}"))
        }

        val imageMarkerRegex = Regex("\\[IMAGE_REF:\\s*(.+?)\\s*\\]")
        
        paragraphs.forEach { p ->
            val trimmed = p.trim()
            if (trimmed.isBlank()) return@forEach
            
            // PROCESS CONTENT WITH POTENTIAL EMBEDDED IMAGES
            // We split the paragraph by the markers and handle each piece
            var lastIndex = 0
            imageMarkerRegex.findAll(trimmed).forEach { match ->
                // Add text before the marker if it's significant
                val textBefore = trimmed.substring(lastIndex, match.range.first).trim()
                if (textBefore.isNotBlank()) {
                    addNarrationOrTitle(textBefore, segments)
                }
                
                // Process the IMAGE
                val rawValue = match.groupValues[1].trim()
                val filename = rawValue.replace("\\s".toRegex(), "")
                
                android.util.Log.d("TextAnalyzer", "🖼️ Found marker in text: '${match.value}' -> cleaned filename: '$filename'")
                
                if (filename.isNotBlank()) {
                    segments.add(ImageSegment(imagePath = filename, caption = null))
                }
                
                lastIndex = match.range.last + 1
            }
            
            // Add remaining text after the last marker
            val remainingText = trimmed.substring(lastIndex).trim()
            if (remainingText.isNotBlank()) {
                addNarrationOrTitle(remainingText, segments)
            }
        }
        return segments
    }

    private fun addNarrationOrTitle(trimmed: String, segments: MutableList<TextSegment>) {
        // 1. Detect explicit style instructions (injected by Extractor)
        val isExplicitlyBold = trimmed.contains("[STYLE:BOLD]")
        val isExplicitlyNormal = trimmed.contains("[STYLE:NORMAL]")
        
        // Detect Drop Cap
        val dropCapRegex = Regex("\\[DROP_CAP:(.)\\]")
        val dropCapMatch = dropCapRegex.find(trimmed)
        val dropCapChar = dropCapMatch?.groupValues?.get(1)
        
        val isBold: Boolean? = when {
            isExplicitlyBold -> true
            isExplicitlyNormal -> false
            else -> null
        }
        
        // 2. Initial Clean of Style Markers (to allow detection of other markers)
        // We do this EARLY so that 'cleanFullText' and 'trimmed' logic below works on content closer to final
        val textWithoutStyle = trimmed
            .replace("[STYLE:BOLD]", "")
            .replace("[STYLE:NORMAL]", "")
            .replace(dropCapRegex, "$1") // Restore Drop Cap character into text
            .trim()

        val hasTitleL = textWithoutStyle.contains("[TITLE_L]")
        val hasTitleM = textWithoutStyle.contains("[TITLE_M]")
        val hasChapterMarker = textWithoutFootnotes(textWithoutStyle) // Helper check for geometric marker if needed, but existing check was direct string contains
        // Re-implementing the check which was lost in my previous thought process or purely based on string
        val hasGeometricTitle = textWithoutStyle.contains("[GEOMETRIC_TITLE]")

        // 3. Clean structural markers for the final text content
        val cleanFullText = textWithoutStyle
            .replace("[TITLE_L]", "")
            .replace("[/TITLE_L]", "")
            .replace("[TITLE_M]", "")
            .replace("[/TITLE_M]", "")
            .replace("[QUOTE]", "")
            .replace("[/QUOTE]", "")
            .replace("[POEM]", "")
            .replace("[/POEM]", "")
            .replace("[GEOMETRIC_TITLE]", "")
            .replace("[/GEOMETRIC_TITLE]", "")
            .trim()
            
        if (cleanFullText.isBlank()) return
        
        // ... (rest of logic) ...
        
        val parts = cleanFullText.split("\n").filter { it.isNotBlank() }
        
        parts.forEachIndexed { index, partText ->
            val isFirstPart = index == 0
            val partTrimmed = partText.trim()
            if (partTrimmed.isEmpty()) return@forEachIndexed
            
            val isPartDialogue = partTrimmed.startsWith("—") || partTrimmed.startsWith("-")
            val partSignificantWords = countSignificantWords(partTrimmed)
            
            val style = when {
                isFirstPart && hasTitleL -> com.example.cititor.domain.model.NarrationStyle.TITLE_LARGE
                isFirstPart && hasTitleM -> com.example.cititor.domain.model.NarrationStyle.TITLE_MEDIUM
                isFirstPart && textWithoutStyle.contains("[QUOTE]") -> com.example.cititor.domain.model.NarrationStyle.DRAMATIC
                isFirstPart && textWithoutStyle.contains("[POEM]") -> com.example.cititor.domain.model.NarrationStyle.POETRY
                isFirstPart && hasGeometricTitle && !isPartDialogue && partSignificantWords <= 6 -> com.example.cititor.domain.model.NarrationStyle.TITLE_LARGE
                else -> com.example.cititor.domain.model.NarrationStyle.`NEUTRAL`
            }
            
            segments.add(NarrationSegment(
                text = partTrimmed,
                intention = ProsodyIntention.NEUTRAL,
                style = style,
                isBold = isBold,
                dropCap = if (isFirstPart) dropCapChar else null // Only first part gets the Drop Cap
            ))
        }
    }
    
    // Helper to keep code clean since I used it above
    private fun textWithoutFootnotes(text: String): Boolean = text.contains("[GEOMETRIC_TITLE]")

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
