package com.example.cititor.domain.dictionary

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages dictionary loading and word validation for intelligent text correction.
 * Supports multiple languages with lazy loading and memory caching.
 */
@Singleton
class DictionaryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "DictionaryManager"
        private const val DICTIONARY_PATH = "dictionaries"
    }

    // Cache of loaded dictionaries by language code
    private val dictionaries = mutableMapOf<String, Set<String>>()

    /**
     * Loads a dictionary for the specified language.
     * Uses lazy loading - only loads when first requested.
     */
    fun loadDictionary(languageCode: String = "es_ES"): Boolean {
        if (dictionaries.containsKey(languageCode)) {
            Log.d(TAG, "Dictionary $languageCode already loaded")
            return true
        }

        return try {
            val startTime = System.currentTimeMillis()
            val words = mutableSetOf<String>()
            
            context.assets.open("$DICTIONARY_PATH/$languageCode.txt").bufferedReader(Charsets.UTF_8).use { reader ->
                reader.lineSequence()
                    .map { line -> 
                        // Take only the first part before any space or tab (handling 'word frequency' format)
                        line.trim().split(Regex("\\s+"))[0].lowercase()
                    }
                    .filter { it.isNotBlank() }
                    .forEach { words.add(it) }
            }
            
            dictionaries[languageCode] = words
            val loadTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Loaded $languageCode dictionary: ${words.size} words in ${loadTime}ms")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load dictionary $languageCode", e)
            false
        }
    }

    /**
     * Checks if a word exists in the dictionary.
     * Case-insensitive search.
     */
    fun contains(word: String, languageCode: String = "es_ES"): Boolean {
        val dictionary = dictionaries[languageCode] ?: run {
            loadDictionary(languageCode)
            dictionaries[languageCode] ?: return false
        }
        return dictionary.contains(word.lowercase())
    }

    /**
     * Attempts to correct a stuck word.
     * [CLEAN VERSION]: Currently returns the word as is.
     * Heuristics removed to prevent over-aggressive splitting.
     */
    fun correctStuckWord(word: String): String {
        return word
    }

    /**
     * Corrects all stuck words in a text.
     * Preserves whitespace and punctuation.
     * Optimized to reduce memory allocations on large texts.
     */
    fun correctText(text: String, languageCode: String = "es_ES"): String {
        if (text.isBlank()) return text
        
        // Ensure dictionary is loaded
        if (!dictionaries.containsKey(languageCode)) {
            loadDictionary(languageCode)
        }

        val result = StringBuilder(text.length)
        // Regex to identify words (including Spanish accents), white spaces AND punctuation tokens
        // [a-zA-ZÁÉÍÓÚÑáéíóúñ]+ is used instead of \w+ to properly handle Unicode in Spanish
        val tokenRegex = Regex("""([a-zA-ZÁÉÍÓÚÑáéíóúñ]+)|(\s+)|([^a-zA-ZÁÉÍÓÚÑáéíóúñ\s]+)""")
        
        tokenRegex.findAll(text).forEach { match ->
            val word = match.groups[1]?.value
            val whitespace = match.groups[2]?.value
            val punctuation = match.groups[3]?.value
            
            when {
                word != null -> {
                    result.append(correctStuckWord(word))
                }
                whitespace != null -> {
                    result.append(whitespace)
                }
                punctuation != null -> {
                    result.append(punctuation)
                }
            }
        }
        
        return result.toString()
    }
}
