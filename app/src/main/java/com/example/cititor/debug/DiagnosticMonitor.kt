package com.example.cititor.debug

import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Singleton to capture and expose diagnostic information about text processing.
 * Useful for debugging why specific words are not being fixed.
 */
object DiagnosticMonitor {
    
    private const val TAG = "DiagnosticMonitor"
    
    // Key: pageIndex, Value: List of states (name to content)
    private val pageTrace = ConcurrentHashMap<Int, MutableList<Pair<String, String>>>()
    
    // List of specific fixes made (e.g., "yque" -> "y que")
    private val auditLog = mutableListOf<String>()

    fun recordState(pageIndex: Int, stateName: String, content: String) {
        val trace = pageTrace.getOrPut(pageIndex) { mutableListOf() }
        trace.add(stateName to content)
        Log.d(TAG, "[PAGE $pageIndex] State recorded: $stateName")
    }

    fun logFix(pageIndex: Int, original: String, fixed: String, method: String) {
        val entry = "[PAGE $pageIndex][$method] '$original' -> '$fixed'"
        auditLog.add(entry)
        Log.i(TAG, entry)
    }

    fun getTraceForPage(pageIndex: Int): List<Pair<String, String>> {
        return pageTrace[pageIndex] ?: emptyList()
    }

    fun dumpToLogcat() {
        Log.i(TAG, "==========================================================")
        Log.i(TAG, "📊 DIAGNOSTIC MONITOR DUMP 📊")
        Log.i(TAG, "Total repairs recorded: ${auditLog.size}")
        auditLog.takeLast(200).forEach { Log.i(TAG, "FIX: $it") }
        Log.i(TAG, "==========================================================")
    }

    fun clear() {
        pageTrace.clear()
        auditLog.clear()
    }
}
