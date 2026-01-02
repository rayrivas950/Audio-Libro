package com.example.cititor.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "characters",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["bookId"])]
)
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: Long,
    val name: String,
    val description: String? = null,
    val gender: String? = null, // "male", "female", "neutral"
    val age: String? = null,    // "child", "young", "adult", "old"
    val basePitch: Float = 1.0f,
    val baseSpeed: Float = 1.0f,
    val customTimbreJson: String? = null // Profile details as JSON
)
