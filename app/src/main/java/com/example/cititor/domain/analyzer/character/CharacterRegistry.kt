package com.example.cititor.domain.analyzer.character

import com.example.cititor.domain.model.Character
import com.example.cititor.domain.model.Gender
import java.util.UUID

class CharacterRegistry {
    private val characters = mutableMapOf<String, Character>() // ID -> Character
    private val aliasMap = mutableMapOf<String, String>() // Alias (lowercase) -> ID
    
    fun register(character: Character) {
        characters[character.id] = character
        aliasMap[character.name.lowercase()] = character.id
        character.aliases.forEach { alias ->
            aliasMap[alias.lowercase()] = character.id
        }
    }
    
    fun getOrCreate(name: String, gender: Gender): Character {
        val normalizedName = name.trim()
        
        // 1. Intentar resolver
        val existing = AliasResolver.resolve(CharacterGuess(normalizedName, gender), this)
        if (existing != null) {
            // Si encontramos un match pero el nombre usado es nuevo, lo agregamos como alias
            if (!existing.aliases.contains(normalizedName) && existing.name != normalizedName) {
                val updatedChar = existing.copy(aliases = existing.aliases + normalizedName)
                register(updatedChar)
                return updatedChar
            }
            return existing
        }
        
        // 2. Crear nuevo
        val newChar = Character(
            id = UUID.randomUUID().toString(),
            name = normalizedName,
            gender = gender,
            aliases = setOf(normalizedName)
        )
        register(newChar)
        return newChar
    }
    
    fun findCharacterByAlias(alias: String): Character? {
        return aliasMap[alias.lowercase()]?.let { characters[it] }
    }
    
    fun findByName(name: String): Character? {
        return characters.values.find { it.name.equals(name, ignoreCase = true) }
    }
    
    fun findByAlias(alias: String): List<Character> {
        val lowerAlias = alias.lowercase()
        return characters.values.filter { char ->
            char.name.lowercase() == lowerAlias || char.aliases.any { it.lowercase() == lowerAlias }
        }
    }

    fun getAll(): List<Character> = characters.values.toList()
}

object AliasResolver {
    
    // Reglas comunes de apodos (hardcoded por ahora)
    private val commonNicknames = mapOf(
        "pepe" to "josé",
        "paco" to "francisco",
        "lola" to "dolores",
        "liz" to "elizabeth",
        "tony" to "antonio",
        "dani" to "daniel",
        "alex" to "alejandro",
        "leo" to "leonardo"
    )

    fun resolve(guess: CharacterGuess, registry: CharacterRegistry): Character? {
        val lowerName = guess.name.lowercase()

        // 1. Si es un nombre explícito, busca match directo o por apodo común
        if (!guess.isAlias) {
            registry.findByName(guess.name)?.let { return it }
            
            // Buscar si es un apodo de alguien existente
            val canonical = commonNicknames[lowerName]
            if (canonical != null) {
                registry.findByName(canonical)?.let { return it }
                // O buscar por alias si el nombre canónico está registrado como alias
                registry.findCharacterByAlias(canonical)?.let { return it }
            }
        }
        
        // 2. Búsqueda por Alias registrado
        registry.findCharacterByAlias(lowerName)?.let { return it }
        
        // 3. Coincidencia Parcial (Startswith/Endswith)
        // "Geralt" match con "Geralt de Rivia"
        registry.getAll().forEach { char ->
            if (char.name.lowercase().contains(lowerName) || 
                char.aliases.any { it.lowercase().contains(lowerName) }) {
                return char
            }
        }
        
        // 4. Desambiguación (Si hubiera múltiples candidatos, aquí iría la lógica)
        // Por ahora, si llegamos aquí y no hay match, retornamos null para que el Registry cree uno nuevo.
        
        return null
    }
}
