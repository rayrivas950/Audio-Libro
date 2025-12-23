package com.example.cititor.domain.analyzer

import com.example.cititor.domain.analyzer.character.CharacterDetector
import com.example.cititor.domain.analyzer.character.CharacterRegistry
import com.example.cititor.domain.analyzer.character.AliasResolver
import com.example.cititor.domain.analyzer.character.CharacterGuess
import com.example.cititor.domain.model.Gender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CharacterIdentificationTest {

    @Test
    fun `detects explicit name in Spanish`() {
        val detector = CharacterDetector()
        val context = "dijo Juan"
        val guess = detector.detectSpeaker(context, "es")
        
        assertNotNull(guess)
        assertEquals("Juan", guess?.name)
        assertEquals(false, guess?.isAlias)
    }

    @Test
    fun `resolves new character`() {
        val registry = CharacterRegistry()
        val char = registry.getOrCreate("Pedro", Gender.MALE)
        
        assertEquals("Pedro", char.name)
        assertEquals(Gender.MALE, char.gender)
    }

    @Test
    fun `resolves alias to existing character`() {
        val registry = CharacterRegistry()
        val original = registry.getOrCreate("Geralt de Rivia", Gender.MALE)
        
        // Register alias manually for test (in real flow, it might be learned or pre-configured)
        val updated = original.copy(aliases = setOf("Geralt", "El Brujo"))
        registry.register(updated)
        
        val resolved = AliasResolver.resolve(CharacterGuess("Geralt", Gender.MALE, isAlias = true), registry)
        
        assertEquals(original.id, resolved?.id)
    }

    @Test
    fun `disambiguates using explicit name priority`() {
        val registry = CharacterRegistry()
        
        // Setup: Two "Brujos"
        val geralt = registry.getOrCreate("Geralt", Gender.MALE).copy(aliases = setOf("Brujo"))
        registry.register(geralt)
        
        val vesemir = registry.getOrCreate("Vesemir", Gender.MALE).copy(aliases = setOf("Brujo"))
        registry.register(vesemir)
        
        // Case: "respondió Vesemir"
        val context = "respondió Vesemir"
        val detector = CharacterDetector()
        val guess = detector.detectSpeaker(context, "es")
        
        assertNotNull(guess)
        assertEquals("Vesemir", guess?.name)
        
        val resolved = AliasResolver.resolve(guess!!, registry)
        assertEquals(vesemir.id, resolved?.id)
    }
    
    @Test
    fun `learns new alias dynamically`() {
        val registry = CharacterRegistry()
        val char = registry.getOrCreate("Elizabeth", Gender.FEMALE)
        
        // "Liz" is a common nickname for Elizabeth in our hardcoded list
        val resolved = registry.getOrCreate("Liz", Gender.FEMALE)
        
        assertEquals(char.id, resolved.id)
        assert(resolved.aliases.contains("Liz"))
    }
}
