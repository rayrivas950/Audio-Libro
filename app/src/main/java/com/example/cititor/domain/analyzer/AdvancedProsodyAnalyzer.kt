package com.example.cititor.domain.analyzer

import com.example.cititor.domain.model.ProsodyInstruction
import com.example.cititor.domain.model.ProsodyIntention
import com.example.cititor.domain.model.TextSegment
import javax.inject.Inject
import javax.inject.Singleton

data class AnalyzedSegment(
    val segment: TextSegment,
    val instruction: ProsodyInstruction,
    val speakerId: Long? = null // Link to CharacterEntity.id
)

/**
 * Orchestrator that combines semantic analysis, character detection, 
 * and professional narration rules.
 */
@Singleton
class AdvancedProsodyAnalyzer @Inject constructor(
    private val intentionAnalyzer: IntentionAnalyzer,
    private val characterDetector: CharacterDetector
) {

    fun analyzePage(text: String, segments: List<TextSegment>, category: com.example.cititor.domain.model.BookCategory = com.example.cititor.domain.model.BookCategory.FICTION): List<AnalyzedSegment> {
        // 1. Identify characters in this specific text blob
        val detections = characterDetector.detectCharacters(text)
        
        // 2. Map segments to instructions
        return segments.map { segment ->
            val intention = intentionAnalyzer.identifyIntention(segment.text)
            val instruction = mapIntentionToInstruction(intention, segment.text, category)
            
            // In a real scenario, we would match segment.speakerId with detections
            // For now, we'll keep it simple for the first implementation
            AnalyzedSegment(segment, instruction)
        }
    }

    private fun mapIntentionToInstruction(
        intention: com.example.cititor.domain.model.ProsodyIntention, 
        text: String,
        category: com.example.cititor.domain.model.BookCategory
    ): ProsodyInstruction {
        var pausePre = 0L
        var pausePost = 200L
        var speed = 1.0f
        var pitch = 1.0f
        var arousal = 0.5f
        var valence = 0.0f

        // --- 1. Category-based base adjustments (Migrated from PatternProsodyEngine) ---
        when (category) {
            com.example.cititor.domain.model.BookCategory.TECHNICAL -> {
                speed *= 0.90f 
                pitch = 0.95f 
            }
            com.example.cititor.domain.model.BookCategory.LEGAL -> {
                speed *= 0.85f 
                pitch = 0.90f 
            }
            com.example.cititor.domain.model.BookCategory.EPIC -> {
                speed *= 1.08f 
                pitch = 1.05f 
            }
            com.example.cititor.domain.model.BookCategory.CHILDREN -> {
                speed *= 0.82f 
                pitch = 1.15f 
            }
            com.example.cititor.domain.model.BookCategory.PHILOSOPHY -> {
                speed *= 0.94f
                pitch = 0.97f
            }
            com.example.cititor.domain.model.BookCategory.COOKING -> {
                speed *= 1.02f
                pitch = 1.0f
            }
            else -> {}
        }

        // Professional Narration Rule: Longer sentences need more air
        if (text.length > 100) {
            pausePost += 150L
            speed *= 0.98f
        }

        // Apply intention-based offsets
        when (intention) {
            com.example.cititor.domain.model.ProsodyIntention.SHOUT -> {
                speed = 1.1f
                pitch = 1.15f
                arousal = 0.9f
                valence = -0.2f
            }
            ProsodyIntention.WHISPER -> {
                speed = 0.85f
                pitch = 0.9f
                arousal = 0.2f
                valence = 0.1f
            }
            ProsodyIntention.SUSPENSE -> {
                pausePost = 500L
                speed = 0.9f
                arousal = 0.6f
            }
            ProsodyIntention.ADRENALINE -> {
                speed = 1.15f
                arousal = 0.85f
            }
            ProsodyIntention.TENSION -> {
                speed = 0.95f
                pausePre = 100L
                arousal = 0.7f
            }
            ProsodyIntention.EMPHASIS -> {
                speed = 0.92f
                pitch = 1.05f
                arousal = 0.65f
            }
            else -> {}
        }

        return ProsodyInstruction(
            pausePre = pausePre,
            pausePost = pausePost,
            speedMultiplier = speed,
            pitchMultiplier = pitch,
            arousal = arousal,
            valence = valence
        )
    }
}
