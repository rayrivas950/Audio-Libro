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
        // 1. Preserve italics/emphasis as *text* for thought detection
        var processedText = text
            .replace("<i>", "*")
            .replace("</i>", "*")
            .replace("<em>", "*")
            .replace("</em>", "*")

        // 2. Remove remaining HTML tags.
        val strippedText = htmlTagRegex.replace(processedText, "")
        
        // 3. Replace sequences of whitespace characters with a single space and trim the result.
        return extraWhitespaceRegex.replace(strippedText, " ").trim()
    }
}
