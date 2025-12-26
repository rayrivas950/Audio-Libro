package com.example.cititor.domain.sanitizer

/**
 * A utility to clean text extracted from books, removing formatting and artifacts.
 */
object TextSanitizer {

    private val htmlTagRegex = "<[^>]*>".toRegex()
    private val extraWhitespaceRegex = "\\s+".toRegex()

    /**
     * Cleans a string by removing all HTML tags and normalizing whitespace.
     * @param text The input string, potentially containing HTML tags and other artifacts.
     * @return A plain text string, ready for TTS processing or display.
     */
    fun sanitize(text: String): String {
        // 1. Handle block-level HTML tags BEFORE stripping to preserve structure
        var processedText = text
            .replace("(?i)<p[^>]*>".toRegex(), "\n\n")
            .replace("(?i)<br[^>]*>".toRegex(), "\n")
            .replace("(?i)<div[^>]*>".toRegex(), "\n")
            .replace("(?i)<li[^>]*>".toRegex(), "\n- ")
            .replace("(?i)<h[1-6][^>]*>".toRegex(), "\n\n")

        // 2. Preserve italics/emphasis as *text* for thought detection
        processedText = processedText
            .replace("<i>", "*")
            .replace("</i>", "*")
            .replace("<em>", "*")
            .replace("</em>", "*")

        // 3. Normalize special characters that might confuse Piper
        processedText = processedText
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("«", "\"")
            .replace("»", "\"")
            .replace("—", "-")
            .replace("–", "-")

        // 4. Remove remaining HTML tags.
        val strippedText = htmlTagRegex.replace(processedText, "")
        
        // 5. Collapse "Spaced Names" (e.g., "A N D R E Z" -> "ANDREZ")
        // Use only single space \s to avoid eating newlines
        val spacedNameRegex = Regex("""([A-ZÁÉÍÓÚÑ])\s(?=[A-ZÁÉÍÓÚÑ]\s|[A-ZÁÉÍÓÚÑ]${'$'})""")
        val collapsedText = spacedNameRegex.replace(strippedText) { it.groupValues[1] }

        // 6. Smart Paragraph Healing & Normalization
        val normalizedLineEndings = collapsedText.replace("\r\n", "\n").replace("\r", "\n")
        
        // Split into all available lines, regardless of how many newlines there are
        val lines = normalizedLineEndings.split("\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            
        val result = StringBuilder()
        for (i in lines.indices) {
            val current = lines[i]
            result.append(current)
            
            val next = lines.getOrNull(i + 1)
            if (next != null) {
                if (shouldJoinLines(current, next)) {
                    result.append(" ")
                } else {
                    result.append("\n\n")
                }
            }
        }
        return result.toString().trim()
    }

    private fun shouldJoinLines(current: String, next: String): Boolean {
        if (current.isEmpty() || next.isEmpty()) return false

        val lastChar = current.last()
        val firstChar = next.first()

        // 1. Strong "Must Join" signals
        // If current ends with a character that usually doesn't end a sentence
        if (lastChar == ',' || lastChar == ';' || lastChar == ':' || lastChar == '-') return true
        
        // If current ends with a lowercase letter and next starts with a lowercase letter
        if (lastChar.isLowerCase() && firstChar.isLowerCase()) return true

        // 2. Strong "Must Break" signals
        // If next looks like a list item or a standalone indicator (e.g. "1. ", "1)", "I.", "- ")
        val indicatorRegex = Regex("""^(\d+|[IVXLCDMivxlcdm]+)[\.\)]?(\s.*|$)""")
        val bulletRegex = Regex("""^[\-\*]\s.*""")
        if (indicatorRegex.matches(next) || bulletRegex.matches(next)) return false
        
        // If current ends with sentence punctuation
        if (lastChar == '.' || lastChar == '!' || lastChar == '?' || lastChar == '"' || lastChar == '»') {
            return false
        }

        // 3. Heuristics for Titles vs Paragraphs
        // If current is short and doesn't have punctuation, it's likely a title/header
        if (current.length < 60) {
            // If it ends with a number (Chapter 1) -> Break
            if (lastChar.isDigit()) return false
            // If it's all caps -> Break
            if (current == current.uppercase() && current.any { it.isLetter() }) return false
            // If next starts with uppercase -> Break (likely new paragraph or title)
            if (firstChar.isUpperCase()) return false
        }

        // 4. Default for everything else
        // If we are here, it's likely a continuation of a paragraph that was physically broken
        return true 
    }
}
