package com.example.cititor.domain.text_extractor

import android.content.Context
import android.net.Uri

interface TextExtractor {
    suspend fun extractText(context: Context, uri: Uri, page: Int): String
    suspend fun getPageCount(context: Context, uri: Uri): Int
}
