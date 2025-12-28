package com.example.cititor.domain.sanitizer

import android.util.Log
import com.example.cititor.domain.dictionary.DictionaryManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A utility to clean text extracted from books, removing formatting and artifacts.
 * Now includes intelligent word correction using dictionary-based validation.
 */
@Singleton
class TextSanitizer @Inject constructor(
    private val dictionaryManager: DictionaryManager
) {

    private val htmlTagRegex = "<[^>]*>".toRegex()
    private val extraWhitespaceRegex = "\\s+".toRegex()

    /**
     * Cleans a string by removing all HTML tags, normalizing whitespace,
     * and correcting stuck words (e.g., "yque" -> "y que").
     * @param text The input string, potentially containing HTML tags and other artifacts.
     * @return A plain text string, ready for TTS processing or display.
     */
    fun sanitize(text: String, pageIndex: Int = -1): String {
        com.example.cititor.debug.DiagnosticMonitor.recordState(pageIndex, "RAW", text)
        
        // 1. Cleaning HTML structure
        var processedText = text
            .replace("(?i)<p[^>]*>".toRegex(), "\n\n")
            .replace("(?i)<br[^>]*>".toRegex(), "\n")
            .replace("(?i)<div[^>]*>".toRegex(), "\n")
            .replace("(?i)<li[^>]*>".toRegex(), "\n- ")
            .replace("(?i)<h[1-6][^>]*>".toRegex(), "\n\n")

        // 2. Clear HTML tags (replace with space to prevent word merger)
        val strippedText = htmlTagRegex.replace(processedText, " ")
        
        // 3. Normalize line endings
        val normalizedLineEndings = strippedText.replace("\r\n", "\n").replace("\r", "\n")
        
        // 4. Basic paragraph healing
        val paragraphs = normalizedLineEndings.split("\n\n")
        val healedParagraphs = paragraphs.map { paragraph ->
            val lines = paragraph.split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            
            if (lines.isEmpty()) return@map ""
            
            val result = StringBuilder()
            for (i in lines.indices) {
                val current = lines[i]
                val next = lines.getOrNull(i + 1)
                
                if (next != null && shouldJoinLines(current, next)) {
                    if (current.endsWith("-")) {
                        result.append(current.dropLast(1)) 
                    } else {
                        result.append(current)
                        result.append(" ")
                    }
                } else {
                    result.append(current)
                    if (next != null) result.append("\n\n")
                }
            }
            result.toString()
        }.filter { it.isNotBlank() }

        val joinedText = healedParagraphs.joinToString("\n\n")
            .replace(Regex("""[ \t]+"""), " ") // Double spaces
            .trim()

        // 5. Dictionary correction (currently returns word as is in clean version)
        val finalFixed = dictionaryManager.correctText(joinedText, pageIndex = pageIndex)
        
        com.example.cititor.debug.DiagnosticMonitor.recordState(pageIndex, "SANITIZED", finalFixed)
        
        return finalFixed
    }

    private fun shouldJoinLines(current: String, next: String): Boolean {
        if (current.isEmpty() || next.isEmpty()) return false

        val lastChar = current.last()
        val firstChar = next.first()

        // 1. Basic Join Signals
        // If current ends with a connector punctuation
        if (lastChar == ',' || lastChar == ';' || lastChar == ':' || lastChar == '-') return true
        
        // If current ends with a lowercase letter and next starts with a lowercase letter
        if (lastChar.isLowerCase() && firstChar.isLowerCase()) return true

        // 2. Dialogue Protection (Mandatory break for common markers)
        val dialogueMarkers = setOf('—', '-', '"', '«', '“')
        if (dialogueMarkers.contains(firstChar)) return false

        // 3. Sentence Ending (Mandatory break)
        val sentencePunctuation = setOf('.', '!', '?')
        if (sentencePunctuation.contains(lastChar)) return false

        // Default: break to be safe and avoid multi-paragraph mergers
        return false 
    }
}
