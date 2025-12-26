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
        
        // 4. Tension Detection (Short, fragmented sentences)
        if (trimmed.length in 5..40 && !trimmed.contains(",") && !trimmed.contains(";")) {
            // "He ran. He hid." -> Tension/Action
            return ProsodyIntention.TENSION
        }
        
        return ProsodyIntention.NEUTRAL
    }
}
