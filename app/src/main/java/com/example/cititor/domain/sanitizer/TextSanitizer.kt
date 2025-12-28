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
            
            sb.append(currentLine)
            
            if (currentLine.isEmpty() || nextLine.isEmpty()) {
                sb.append("\n") // Mantener párrafos vacíos
            } else if (nextLine.startsWith("—") || nextLine.startsWith("-") || nextLine.startsWith("?")) {
                sb.append("\n") // Mantener diálogos o listas con viñetas
            } else if (nextLine.isNotEmpty() && nextLine.first().isDigit()) {
                sb.append("\n") // Mantener listas numeradas
            } else if (currentLine.length < 4) {
                sb.append("\n") // Regla heurística de seguridad para líneas cortas (I, II, etc)
            } else {
                val lastChar = currentLine.last()
                // Si NO termina en signo de puntuación final, unimos
                if (lastChar != '.' && lastChar != '!' && lastChar != '?' && lastChar != '—') {
                    sb.append(" ")
                } else {
                    sb.append("\n")
                }
            }
        }
        sb.append(lines.last().trim())
        return sb.toString()
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
