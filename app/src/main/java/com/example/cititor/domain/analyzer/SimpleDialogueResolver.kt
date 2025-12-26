package com.example.cititor.domain.analyzer

import com.example.cititor.domain.model.DialogueSegment
import com.example.cititor.domain.model.NarrationSegment
import com.example.cititor.domain.model.TextSegment

/**
 * A simple implementation of DialogueResolver that distinguishes between
 * narrator and a generic character based on segment type.
 */
class SimpleDialogueResolver : DialogueResolver {
    override fun resolveSpeaker(segment: TextSegment, context: List<TextSegment>): String {
        return when (segment) {
            is NarrationSegment -> "Narrator"
            is DialogueSegment -> "Character_Generic"
        }
    }
}
