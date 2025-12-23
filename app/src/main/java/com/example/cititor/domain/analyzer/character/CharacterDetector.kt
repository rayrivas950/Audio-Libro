package com.example.cititor.domain.analyzer.character

import com.example.cititor.domain.model.Gender
import java.util.Locale

data class CharacterGuess(
    val name: String,
    val gender: Gender,
    val isAlias: Boolean = false
)

class CharacterDetector {

    // Regex simplificado para encontrar nombres capitalizados.
    // Evita inicio de oración si es posible (requiere análisis más complejo, por ahora heurística simple).
    // Busca: "dijo [Nombre]" o "[Nombre] dijo"
    // Lista de verbos de dicción comunes (podría inyectarse o compartirse con EmotionRules)
    private val speechVerbs = listOf(
        "dijo", "respondió", "preguntó", "exclamó", "susurró", "gritó", "añadió", "contestó",
        "said", "replied", "asked", "exclaimed", "whispered", "shouted", "added", "answered"
    )

    fun detectSpeaker(contextText: String?, language: String): CharacterGuess? {
        if (contextText.isNullOrBlank()) return null

        val lowerContext = contextText.lowercase(Locale.getDefault())
        
        // 1. Prioridad: Nombre Explícito cerca de verbo de dicción
        // Estrategia: Buscar verbo y mirar palabras capitalizadas adyacentes.
        
        for (verb in speechVerbs) {
            val verbIndex = lowerContext.indexOf(verb)
            if (verbIndex != -1) {
                // Buscar antes y después del verbo en el texto ORIGINAL (para ver mayúsculas)
                val explicitName = findCapitalizedNameAround(contextText, verbIndex, verb.length)
                if (explicitName != null) {
                    return CharacterGuess(explicitName, inferGender(contextText, language), isAlias = false)
                }
            }
        }

        // 2. Prioridad: Alias/Título (heurística básica por ahora)
        // Si no hay nombre propio, buscar sustantivos comunes precedidos por artículo (el brujo, la reina)
        // Esto es complejo sin NLP real, lo dejaremos para una iteración futura o lista blanca.
        
        return null
    }

    private fun findCapitalizedNameAround(text: String, index: Int, length: Int): String? {
        // Look after: "dijo Juan"
        val after = text.substring((index + length).coerceAtMost(text.length)).trim()
        val firstWordAfter = after.split(Regex("\\s+")).firstOrNull()?.replace(Regex("[^a-zA-ZáéíóúÁÉÍÓÚñÑ]"), "")
        
        if (firstWordAfter != null && firstWordAfter.isNotEmpty() && firstWordAfter[0].isUpperCase()) {
            // Filtrar palabras comunes que pueden ir capitalizadas por error o inicio de frase falsa
            if (firstWordAfter.length > 1) return firstWordAfter
        }

        // Look before: "Juan dijo"
        val before = text.substring(0, index).trim()
        val lastWordBefore = before.split(Regex("\\s+")).lastOrNull()?.replace(Regex("[^a-zA-ZáéíóúÁÉÍÓÚñÑ]"), "")
        
        if (lastWordBefore != null && lastWordBefore.isNotEmpty() && lastWordBefore[0].isUpperCase()) {
             if (lastWordBefore.length > 1) return lastWordBefore
        }
        
        return null
    }

    private fun inferGender(context: String, language: String): Gender {
        val lower = context.lowercase(Locale.getDefault())
        if (language == "es") {
            if (lower.contains(" el ") || lower.contains(" él ") || lower.contains("lo ")) return Gender.MALE
            if (lower.contains(" la ") || lower.contains(" ella ") || lower.contains("la ")) return Gender.FEMALE
            // Adjetivos: cansado/cansada
            if (lower.contains("o ")) return Gender.MALE // Muy naive
            if (lower.contains("a ")) return Gender.FEMALE // Muy naive
        } else {
            if (lower.contains(" he ") || lower.contains(" him ") || lower.contains(" his ")) return Gender.MALE
            if (lower.contains(" she ") || lower.contains(" her ")) return Gender.FEMALE
        }
        return Gender.UNKNOWN
    }
}
