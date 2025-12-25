package com.example.cititor.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class BookMetadata(
    val characters: List<Character>,
    val properNames: Set<String>
)
