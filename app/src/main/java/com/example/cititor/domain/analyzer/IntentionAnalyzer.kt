package com.example.cititor.domain.analyzer

import com.example.cititor.domain.model.ProsodyIntention
import java.util.Locale

/**
 * Specialist in finding semantic cues and structural patterns in text 
 * to determine the prosody intention.
 */
class IntentionAnalyzer {
    
    private val shoutKeywords = setOf("gritó", "exclamó", "vociferó", "bramó", "shouted", "exclaimed", "rugió")
    private val whisperKeywords = setOf("susurró", "murmuró", "musitó", "whispered", "muttered", "silbó")
    private val solemnKeywords = setOf("declaró", "proclamó", "decretó", "rezo", "oró", "juró")
    private val adrenalineKeywords = setOf("corrió", "saltó", "golpeó", "disparó", "huyó", "escapó", "rápido")
    
    fun identifyIntention(text: String): ProsodyIntention {
        val lower = text.lowercase(Locale.getDefault())
        val trimmed = text.trim()
        
        // 1. Structural & Punctuation patterns (Highest Priority)
        if (lower.contains("!!!") || (lower.count { it == '!' } >= 2)) return ProsodyIntention.SHOUT
        if (trimmed.endsWith("...")) return ProsodyIntention.SUSPENSE
        
        // 2. Action/Adrenaline Detection (Short, energetic sentences)
        if (adrenalineKeywords.any { lower.contains(it) } && text.length < 100) {
            return ProsodyIntention.ADRENALINE
        }
        
        // 3. Keyword based (Verbs of Diction)
        if (shoutKeywords.any { lower.contains(it) }) return ProsodyIntention.SHOUT
        if (whisperKeywords.any { lower.contains(it) }) return ProsodyIntention.WHISPER
        if (solemnKeywords.any { lower.contains(it) }) return ProsodyIntention.SOLEMN
        
        // 4. Gravity Detector (Priority over Tension)
        // Detect capitalized words that are NOT at the start of definition
        val words = trimmed.split(" ")
        var properNames = 0
        val stopWords = setOf("El", "La", "Los", "Las", "Un", "Una", "Y", "De", "En", "A")

        words.forEachIndexed { index, word ->
            if (word.isNotEmpty() && word[0].isUpperCase()) {
                val cleanWord = word.filter { it.isLetter() }
                // Use a heuristic: If it's capitalized and NOT a common start word, 
                // OR if it's mid-sentence capitalized, count it.
                if (index > 0 || !stopWords.contains(cleanWord)) {
                    if (cleanWord.length > 2) { // Filter 'A', 'Y'
                         properNames++
                    }
                }
            }
        }
        
        // If density is high (more than 1 proper name), add gravity
        if (properNames >= 2) {
            return ProsodyIntention.EMPHASIS
        }

        // 5. Tension Detection (Short, fragmented sentences)
        if (trimmed.length in 5..40 && !trimmed.contains(",") && !trimmed.contains(";")) {
            // "He ran. He hid." -> Tension/Action
            return ProsodyIntention.TENSION
        }
        
        return ProsodyIntention.NEUTRAL
    }
}
