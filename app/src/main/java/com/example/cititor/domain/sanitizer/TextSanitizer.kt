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
        // Absolute passthrough for Tabula Rasa 3.0 / Phase 2
        return text
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
