package com.example.cititor.domain.analyzer

import android.util.Log
import com.example.cititor.domain.dictionary.DictionaryManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Audit and repair text consistency where standard dictionary checks might fail.
 * Focuses on high-probability patterns of stuck words from PDF extractions.
 */
@Singleton
class ConsistencyAuditor @Inject constructor(
    private val dictionaryManager: DictionaryManager
) {
    companion object {
        private const val TAG = "ConsistencyAuditor"
    }

    /**
     * Performs a final pass on the text to fix obvious high-probability errors.
     * [CLEAN VERSION]: Only performs minimal whitespace normalization.
     */
    fun auditAndRepair(text: String, pageIndex: Int = -1): String {
        if (text.isBlank()) return text
        
        val startTime = System.currentTimeMillis()
        var repairedText = text

        // Minimal whitespace normalization
        repairedText = repairedText
            .replace(Regex("""[\t ]+"""), " ") // Double spaces
            .trim()

        Log.d(TAG, "Audit and repair (minimal) completed in ${System.currentTimeMillis() - startTime}ms")
        return repairedText
    }
}
