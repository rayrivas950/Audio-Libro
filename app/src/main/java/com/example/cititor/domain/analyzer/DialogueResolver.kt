package com.example.cititor.domain.analyzer

import com.example.cititor.domain.model.TextSegment

/**
 * Interface for components that identify speakers and resolve dialogue associations.
 */
interface DialogueResolver {
    /**
     * Identifies the speaker or context of a segment.
     * @param segment The segment to analyze.
     * @param context Preceding and succeeding segments for context.
     * @return A speaker identifier (e.g., "Narrator", "Character_1").
     */
    fun resolveSpeaker(segment: TextSegment, context: List<TextSegment>): String
}
