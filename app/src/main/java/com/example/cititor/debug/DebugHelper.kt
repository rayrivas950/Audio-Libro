package com.example.cititor.debug

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.cititor.domain.analyzer.TextAnalyzer
import com.example.cititor.domain.model.DialogueSegment
import com.example.cititor.domain.model.NarrationSegment
import com.example.cititor.domain.model.TextSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Helper para diagnóstico y pruebas de componentes críticos.
 * Muestra resultados en Toast y Log para debugging sin ADB.
 */
object DebugHelper {
    
    private const val TAG = "DebugHelper"
    
    /**
     * Prueba 1: Serialización básica de TextSegment
     */
    suspend fun testSerialization(context: Context, json: Json) = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        
        try {
            // Test 1: NarrationSegment
            val narration = NarrationSegment("Esta es una narración de prueba.")
            val narrationJson = json.encodeToString<TextSegment>(narration)
            results.add("✅ Narration: $narrationJson")
            Log.d(TAG, "Narration JSON: $narrationJson")
            
            // Test 2: DialogueSegment
            val dialogue = DialogueSegment("¡Hola mundo!")
            val dialogueJson = json.encodeToString<TextSegment>(dialogue)
            results.add("✅ Dialogue: $dialogueJson")
            Log.d(TAG, "Dialogue JSON: $dialogueJson")
            
            // Test 3: Lista mixta
            val segments = listOf<TextSegment>(
                NarrationSegment("Texto 1"),
                DialogueSegment("Diálogo 1"),
                NarrationSegment("Texto 2")
            )
            val listJson = json.encodeToString(segments)
            results.add("✅ List: ${listJson.take(100)}...")
            Log.d(TAG, "List JSON: $listJson")
            
            // Test 4: Deserialización
            val deserialized = json.decodeFromString<List<TextSegment>>(listJson)
            results.add("✅ Deserialized: ${deserialized.size} segments")
            Log.d(TAG, "Deserialized successfully: ${deserialized.size} segments")
            
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Serialization Tests PASSED ✅", Toast.LENGTH_LONG).show()
            }
            
        } catch (e: Exception) {
            val error = "❌ Serialization FAILED: ${e.javaClass.simpleName} - ${e.message}"
            results.add(error)
            Log.e(TAG, error, e)
            e.printStackTrace()
            
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Serialization FAILED ❌", Toast.LENGTH_LONG).show()
            }
        }
        
        results.joinToString("\n")
    }
    
    /**
     * Prueba 2: TextAnalyzer
     */
    suspend fun testTextAnalyzer(context: Context, json: Json) = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        
        try {
            // Test con texto que contiene diálogos
            val testText = """
                Era una noche oscura. "¿Quién anda ahí?" preguntó Juan.
                Nadie respondió. "Sal de ahí" insistió.
            """.trimIndent()
            
            // For testing in a static helper, we manually instantiate dependencies
            val dictionaryManager = com.example.cititor.domain.dictionary.DictionaryManager(context)
            val textSanitizer = com.example.cititor.domain.sanitizer.TextSanitizer(dictionaryManager)
            val consistencyAuditor = com.example.cititor.domain.analyzer.ConsistencyAuditor(dictionaryManager)
            val analyzer = TextAnalyzer(
                textSanitizer,
                com.example.cititor.domain.analyzer.CharacterDetector(),
                com.example.cititor.domain.analyzer.IntentionAnalyzer(),
                consistencyAuditor
            )
            val segments = analyzer.analyze(testText)
            results.add("✅ Analyzed: ${segments.size} segments")
            Log.d(TAG, "TextAnalyzer produced ${segments.size} segments")
            
            segments.forEachIndexed { index, segment ->
                val type = when(segment) {
                    is NarrationSegment -> "Narration"
                    is DialogueSegment -> "Dialogue"
                    is com.example.cititor.domain.model.ImageSegment -> "Image"
                }
                results.add("  [$index] $type: ${segment.text.take(30)}...")
                Log.d(TAG, "Segment $index ($type): ${segment.text}")
            }
            
            // Intentar serializar el resultado
            val serialized = json.encodeToString(segments)
            results.add("✅ Serialized: ${serialized.length} chars")
            Log.d(TAG, "Serialized successfully: ${serialized.length} characters")
            
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "TextAnalyzer Tests PASSED ✅", Toast.LENGTH_LONG).show()
            }
            
        } catch (e: Exception) {
            val error = "❌ TextAnalyzer FAILED: ${e.javaClass.simpleName} - ${e.message}"
            results.add(error)
            Log.e(TAG, error, e)
            e.printStackTrace()
            
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "TextAnalyzer FAILED ❌", Toast.LENGTH_LONG).show()
            }
        }
        
        results.joinToString("\n")
    }
    
    /**
     * Muestra un mensaje de debug en Toast y Log
     */
    fun showDebug(context: Context, tag: String, message: String) {
        Log.d(tag, message)
        Toast.makeText(context, "$tag: $message", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Muestra un error en Toast y Log
     */
    fun showError(context: Context, tag: String, message: String, error: Throwable? = null) {
        Log.e(tag, message, error)
        error?.printStackTrace()
        Toast.makeText(context, "❌ $tag: $message", Toast.LENGTH_LONG).show()
    }
}
