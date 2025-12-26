package com.example.cititor.domain.model

import kotlinx.serialization.Serializable

/**
 * Represents the category or genre of a book, used to adapt the narrating style.
 */
@Serializable
enum class BookCategory {
    FICTION,     // Standard novel, balanced prosody
    TECHNICAL,   // Manuals, neutral and clear, slower pace
    LEGAL,       // Formal, precise, specific pausing
    EPIC,        // Dramatic, higher dynamic range in pitch
    CHILDREN,    // Expressive, slower, higher pitch variations
    JOURNALISM   // Informative, steady rhythm
}
