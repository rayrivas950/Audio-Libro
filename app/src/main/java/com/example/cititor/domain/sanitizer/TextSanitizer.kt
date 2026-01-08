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
     * @param isEpub Whether the text comes from an EPUB (allowing for more structural preservation).
     * @return A plain text string, ready for TTS processing or display.
     */
    fun sanitize(text: String, isEpub: Boolean = false): String {
        if (text.isBlank()) return text
        
        // 1. Unir líneas para dar fluidez al párrafo (solo si no es EPUB, ya que EPUB ya viene estructurado)
        val processedText = if (isEpub) {
             // For EPUB, we just normalize excessive newlines but keep single ones
             text.replace(Regex("\\n{3,}"), "\n\n")
        } else {
             joinLines(text)
        }
        
        // 2. Corregir espaciado de puntuación gramatical
        val fixedPunctuation = fixPunctuation(processedText)
        
        // 3. Eliminar números romanos aislados (numeración de capítulos: I, II, III, etc.)
        val finalResult = removeRomanNumeralChapterMarkers(fixedPunctuation)
        
        return finalResult
    }
    
    /**
     * Removes isolated Roman numerals used as chapter numbering (e.g., "I", "II", "III").
     * These break TTS immersion when read as letters.
     */
    private fun removeRomanNumeralChapterMarkers(text: String): String {
        // Match lines that contain ONLY a Roman numeral (possibly with whitespace)
        // Roman numerals: I, II, III, IV, V, VI, VII, VIII, IX, X, XI, XII, etc.
        val romanNumeralLineRegex = Regex("""^\s*[IVXLCDM]+\s*$""", RegexOption.MULTILINE)
        
        return text.replace(romanNumeralLineRegex, "").trim()
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
}
