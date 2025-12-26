package com.example.cititor.domain.analyzer

import com.example.cititor.domain.model.ProsodyIntention
import java.util.Locale

/**
 * Specialist in finding semantic cues in text to determine the prosody intention.
 */
class IntentionAnalyzer {
    
    private val shoutKeywords = setOf("gritó", "exclamó", "vociferó", "bramó", "shouted", "exclaimed")
    private val whisperKeywords = setOf("susurró", "murmuró", "musitó", "whispered", "muttered")
    
    fun identifyIntention(text: String): ProsodyIntention {
        val lower = text.lowercase(Locale.getDefault())
        
        // 1. Punctuation based
        if (lower.contains("!!!") || (lower.count { it == '!' } >= 2)) return ProsodyIntention.SHOUT
        if (lower.endsWith("...")) return ProsodyIntention.SUSPENSE
        
        // 2. Keyword based (Action verbs)
        if (shoutKeywords.any { lower.contains(it) }) return ProsodyIntention.SHOUT
        if (whisperKeywords.any { lower.contains(it) }) return ProsodyIntention.WHISPER
        
        // 3. Short sentence tension
        if (text.length in 5..25 && !text.contains(",")) {
            // Potentially high tension or quick action
            // return ProsodyIntention.TENSION (Optional refinement)
        }
        
        return ProsodyIntention.NEUTRAL
    }
}
