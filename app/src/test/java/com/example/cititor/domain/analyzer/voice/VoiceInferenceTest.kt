package com.example.cititor.domain.analyzer.voice

import com.example.cititor.domain.model.AgeRange
import com.example.cititor.domain.model.Character
import com.example.cititor.domain.model.Gender
import com.example.cititor.domain.model.VoiceProfiles
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class VoiceInferenceTest {

    private val engine = VoiceInferenceEngine()

    @Test
    fun `infers default male hero`() {
        val char = Character(
            id = "1",
            name = "Juan",
            gender = Gender.MALE,
            ageRange = AgeRange.ADULT
        )
        val profile = engine.inferProfile(char)
        assertEquals(VoiceProfiles.HERO_MALE.id, profile.id)
    }

    @Test
    fun `infers mysterious profile from name`() {
        val char = Character(
            id = "2",
            name = "La Sombra",
            gender = Gender.UNKNOWN
        )
        val profile = engine.inferProfile(char)
        assertEquals(VoiceProfiles.MYSTERIOUS.id, profile.id)
    }

    @Test
    fun `infers giant profile from description`() {
        val char = Character(
            id = "3",
            name = "Grog",
            gender = Gender.MALE
        )
        val description = "Era un gigante enorme y brutal."
        val profile = engine.inferProfile(char, description)
        assertEquals(VoiceProfiles.GIANT.id, profile.id)
    }

    @Test
    fun `infers child profile from age`() {
        val char = Character(
            id = "4",
            name = "Timmy",
            gender = Gender.MALE,
            ageRange = AgeRange.CHILD
        )
        val profile = engine.inferProfile(char)
        assertEquals(VoiceProfiles.CHILD.id, profile.id)
    }
}
