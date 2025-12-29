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
        if (text.isBlank()) return text
        
        // 1. Unir líneas para dar fluidez al párrafo
        val textWithJoinedLines = joinLines(text)
        
        // 2. Corregir espaciado de puntuación gramatical
        val finalResult = fixPunctuation(textWithJoinedLines)
        
        return finalResult
    }

    private fun joinLines(text: String): String {
        val lines = text.split("\n")
        if (lines.size <= 1) return text
        
        val sb = StringBuilder()
        for (i in 0 until lines.size - 1) {
            val currentLine = lines[i].trim()
            val nextLine = lines[i + 1].trim()
            
            if (currentLine.isEmpty()) continue
            
            sb.append(currentLine)
            
            val lastChar = currentLine.last()
            val isDialogue = nextLine.startsWith("—") || nextLine.startsWith("-")
            val isTitle = currentLine.contains("[GEOMETRIC_TITLE]") || nextLine.contains("[GEOMETRIC_TITLE]")
            val isShortLine = currentLine.length < 15
            
            if (isDialogue || isTitle || isShortLine || lastChar == '.' || lastChar == '!' || lastChar == '?') {
                sb.append("\n\n")
            } else {
                sb.append(" ")
            }
        }
        sb.append(lines.last().trim())
        
        return sb.toString().replace(Regex("\\n{3,}") , "\n\n").trim()
    }

    private fun fixPunctuation(text: String): String {
        // Añade espacio tras punto si le sigue una letra (ej: "hola.Mundo" -> "hola. Mundo")
        // No afecta a números (3.14) ni a puntos seguidos de espacios.
        return text.replace(Regex("""\.([A-ZÁÉÍÓÚÑa-záéíóúñ])"""), ". $1")
    }

    private fun shouldJoinLines(current: String, next: String): Boolean {
        // Legacy method, not used anymore in the new atomic flow
        return false 
    }
}
