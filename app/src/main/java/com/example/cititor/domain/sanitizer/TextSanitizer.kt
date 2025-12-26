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
        // 1. Pre-process block-level HTML tags to preserve structure
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

        // 3. Normalize special characters
        processedText = processedText
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("«", "\"")
            .replace("»", "\"")
            .replace("–", "-")

        // 3b. Protect complex dialogue markers stuck to punctuation (e.g. "noche.—")
        // We force a double newline to ensure they are treated as separate paragraphs
        val compositeDialogueRegex = Regex("""([\.!\?])\s*([—\-"«“])""")
        processedText = compositeDialogueRegex.replace(processedText, "$1\n\n$2")

        // 4. Remove remaining HTML tags, but REPLACE WITH SPACE to avoid word collapsing
        // e.g. "por</span><span>la" -> "por la" instead of "porla"
        val strippedText = htmlTagRegex.replace(processedText, " ")
        
        // 5. Normalizar saltos de línea
        val normalizedLineEndings = strippedText.replace("\r\n", "\n").replace("\r", "\n")
        
        // 6. Fix missing space after punctuation (very common in bad extractions)
        // e.g. "Cordeleros.Entró" -> "Cordeleros. Entró"
        val punctSpaceRegex = Regex("""([\.!\?])([A-ZÁÉÍÓÚÑ])""")
        var fixedPunctuation = punctSpaceRegex.replace(normalizedLineEndings, "$1 $2")

        // 7. Colapsar 3 o más saltos de línea en 2 (párrafo estándar)
        val paragraphGrouped = fixedPunctuation.replace(Regex("""\n{3,}"""), "\n\n")

        // 8. Healing de párrafos (Unir líneas cortadas por el PDF que pertenecen al mismo párrafo)
        val paragraphs = paragraphGrouped.split("\n\n")
        val healedParagraphs = paragraphs.map { paragraph ->
            val lines = paragraph.split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            
            if (lines.isEmpty()) return@map ""
            
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
            result.toString()
        }.map { it.trim() }.filter { it.isNotBlank() }

        return healedParagraphs.joinToString("\n\n")
            .replace(Regex("""[ \t]+"""), " ") // Colapsar espacios múltiples horizontales
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
    }

    private fun shouldJoinLines(current: String, next: String): Boolean {
        if (current.isEmpty() || next.isEmpty()) return false

        val lastChar = current.last()
        val firstChar = next.first()

        // 1. DIALOGUE PROTECTION (Rule of Gold)
        // If the next line starts with a dialogue marker, it MUST be a new paragraph.
        // Handled markers: Em-dash, hyphen, guillemets, standard quotes.
        val dialogueMarkers = setOf('—', '-', '"', '«', '“')
        if (dialogueMarkers.contains(firstChar)) return false

        // 2. Page Number / Noise filtering
        // If next is just a number (e.g., "5"), it's likely a page number artifact.
        val digitsOnlyRegex = Regex("""^\d+$""")
        if (digitsOnlyRegex.matches(next)) return false

        // 3. Strong "Must Join" signals
        // If current ends with a character that usually doesn't end a sentence
        if (lastChar == ',' || lastChar == ';' || lastChar == ':' || lastChar == '-') return true
        
        // If current ends with a lowercase letter and next starts with a lowercase letter
        if (lastChar.isLowerCase() && firstChar.isLowerCase()) return true

        // 4. Strong "Must Break" signals
        // If next looks like a list item or a standalone indicator (e.g. "1. ", "1)", "I.", "- ")
        val indicatorRegex = Regex("""^(\d+|[IVXLCDMivxlcdm]+)[\.\)]?(\s.*|$)""")
        val bulletRegex = Regex("""^[\-\*]\s.*""")
        if (indicatorRegex.matches(next) || bulletRegex.matches(next)) return false
        
        // If current ends with sentence punctuation
        val sentencePunctuation = setOf('.', '!', '?', '"', '»', '”', '—')
        if (sentencePunctuation.contains(lastChar)) {
            // SPECIAL CASE: If it ends with —. or —? or —! (End of an acotación)
            // it SHOULD join with the next line of the same paragraph.
            if (current.length >= 2 && current[current.length - 2] == '—') {
                return true
            }
            return false
        }

        // 5. Heuristics for Titles vs Paragraphs
        // If current is short and doesn't have punctuation, it's likely a title/header
        if (current.length < 60) {
            // If it ends with a number (Chapter 1) -> Break
            if (lastChar.isDigit()) return false
            // If it's all caps -> Break
            if (current == current.uppercase() && current.any { it.isLetter() }) return false
            // If next starts with uppercase -> Break (likely new paragraph or title)
            if (firstChar.isUpperCase()) return false
        }

        // 6. Default for everything else
        // If we are here, it's likely a continuation of a paragraph that was physically broken
        return true 
    }
}
