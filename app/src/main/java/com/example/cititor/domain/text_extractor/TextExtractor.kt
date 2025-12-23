package com.example.cititor.domain.text_extractor

import android.content.Context
import android.net.Uri

/**
 * Interface for extracting text content from different file formats.
 */
interface TextExtractor {
    /**
     * Extracts text from a specific page/section of a document.
     */
    suspend fun extractText(context: Context, uri: Uri, page: Int): String
    
    /**
     * Extracts text from all pages/sections in a single batch operation.
     * More efficient than calling extractText() multiple times.
     */
    suspend fun extractAllPages(context: Context, uri: Uri): List<String>
    
    /**
     * Gets the total number of pages/sections in the document.
     */
    suspend fun getPageCount(context: Context, uri: Uri): Int
}
