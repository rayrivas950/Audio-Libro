package com.example.cititor.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "prosody_scripts",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId", "pageIndex"])]
)
data class ProsodyScriptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val pageIndex: Int,
    val segmentIndex: Int,
    val text: String,
    val speakerId: Long? = null, // FK to CharacterEntity.id, null means NARRATOR
    val arousal: Float = 0.5f,
    val valence: Float = 0.0f,
    val dominance: Float = 0.5f,
    val pausePre: Long = 0,
    val pausePost: Long = 0,
    val speedMultiplier: Float = 1.0f,
    val pitchMultiplier: Float = 1.0f,
    val volumeMultiplier: Float = 1.0f
)
