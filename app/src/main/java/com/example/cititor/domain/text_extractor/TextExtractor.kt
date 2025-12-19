package com.example.cititor.domain.text_extractor

import android.net.Uri

interface TextExtractor {
    suspend fun extractText(uri: Uri, page: Int): String
    suspend fun getPageCount(uri: Uri): Int
}
