package com.example.cititor.domain.analyzer

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class CharacterDetection(
    val name: String,
    val description: String? = null,
    val genderHeuristic: String? = null
)

/**
 * Advanced detector that scans text for character introductions and dialogues.
 */
@Singleton
class CharacterDetector @Inject constructor() {

    private val dictionVerbs = setOf(
        "dijo", "gritó", "susurró", "exclamó", "murmuró", "preguntó", "contestó", 
        "replicó", "añadió", "balbuceó", "comentó", "exclamó", "rugió", "vociferó"
    )

    private val genderMaleMarkers = setOf("él", "lo", "el", "señor", "niño", "hombre", "ronco", "grueso")
    private val genderFemaleMarkers = setOf("ella", "la", "señora", "niña", "mujer", "suave", "aguda")

    // Regex to match: — dialogue — dictionVerb Name.
    // Group 1: Dialogue content, Group 2: Diction verb, Group 3: Character Name
    private val dialogueDictionRegex = Regex("""—\s*(.*?)\s*—\s*(${dictionVerbs.joinToString("|")})\s+([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)*)""", RegexOption.IGNORE_CASE)
    
    // Regex to match capitalized words (Potential Names)
    private val nameRegex = Regex("""[A-ZÁÉÍÓÚÑ][a-záéíóúñ]{2,}""")

    fun detectCharacters(text: String): List<CharacterDetection> {
        val found = mutableMapOf<String, CharacterDetection>()
        
        // 1. Scan for dialogue attribution
        dialogueDictionRegex.findAll(text).forEach { match ->
            val name = match.groupValues[3].trim()
            if (isValidName(name)) {
                val context = text.substring(
                    (match.range.first - 100).coerceAtLeast(0),
                    (match.range.last + 100).coerceAtMost(text.length)
                )
                val description = extractDescription(context, name)
                val gender = guessGender(context, name)
                
                updateOrAdd(found, name, description, gender)
            }
        }
        
        return found.values.toList()
    }

    private fun updateOrAdd(map: MutableMap<String, CharacterDetection>, name: String, desc: String?, gender: String?) {
        val existing = map[name]
        if (existing == null) {
            map[name] = CharacterDetection(name, desc, gender)
        } else {
            // Merge descriptions if new one is better
            val newDesc = if (existing.description == null) desc else existing.description
            val newGender = if (existing.genderHeuristic == null) gender else existing.genderHeuristic
            map[name] = existing.copy(description = newDesc, genderHeuristic = newGender)
        }
    }

    private fun isValidName(name: String): Boolean {
        val stopWords = setOf("El", "La", "Los", "Las", "Un", "Una", "De", "En", "A", "Y", "Pero")
        return name.split(" ").all { it !in stopWords } && name.length >= 3
    }

    private fun extractDescription(context: String, name: String): String? {
        val lower = context.lowercase(Locale.getDefault())
        val adjectives = setOf("voz", "ronca", "suave", "grave", "aguda", "profunda", "clara", "quebrada")
        
        return adjectives.find { lower.contains(it) }?.let { "Tiene indicios de $it" }
    }

    private fun guessGender(context: String, name: String): String? {
        val lower = context.lowercase(Locale.getDefault())
        val maleCount = genderMaleMarkers.count { lower.contains(it) }
        val femaleCount = genderFemaleMarkers.count { lower.contains(it) }
        
        return when {
            maleCount > femaleCount -> "male"
            femaleCount > maleCount -> "female"
            else -> null
        }
    }
}
