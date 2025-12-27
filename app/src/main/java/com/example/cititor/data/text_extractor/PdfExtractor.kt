package com.example.cititor.data.text_extractor

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.cititor.domain.text_extractor.TextExtractor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfExtractor @Inject constructor() : TextExtractor {

    companion object {
        private const val TAG = "PdfExtractor"
    }

    override suspend fun extractText(context: Context, uri: Uri, page: Int): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "Extracting text from PDF page $page, URI: $uri")
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                val errorMsg = "Could not open input stream for URI: $uri"
                Log.e(TAG, errorMsg)
                return@withContext errorMsg
            }
            
            inputStream.use { stream ->
                PDDocument.load(stream).use { document ->
                    Log.d(TAG, "PDF loaded successfully, total pages: ${document.numberOfPages}")
                    
                    if (page >= 0 && page < document.numberOfPages) {
                        val stripper = GeometryPDFStripper()
                        stripper.startPage = page + 1
                        stripper.endPage = page + 1
                        stripper.sortByPosition = true
                        stripper.lineSeparator = "\n"
                        stripper.spacingTolerance = 0.1f // Aggressive checking for spaces (fixes "yque")
 
                        val text = stripper.getText(document)
                        Log.d(TAG, "Successfully extracted ${text.length} characters from page $page")
                        text
                    } else {
                        val errorMsg = "Page index out of bounds. Requested: $page, Available: 0-${document.numberOfPages - 1}"
                        Log.e(TAG, errorMsg)
                        errorMsg
                    }
                }
            }
        } catch (e: IOException) {
            val errorMsg = "IOException while extracting text from PDF: ${e.message}"
            Log.e(TAG, errorMsg, e)
            e.printStackTrace()
            "Error extracting text from PDF: ${e.message}"
        } catch (e: Exception) {
            val errorMsg = "Unexpected error extracting text from PDF: ${e.javaClass.simpleName} - ${e.message}"
            Log.e(TAG, errorMsg, e)
            e.printStackTrace()
            "Error extracting text from PDF: ${e.message}"
        }
    }

    override suspend fun extractPages(
        context: Context,
        uri: Uri,
        onPageExtracted: suspend (Int, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Streaming all pages from PDF, URI: $uri")
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Could not open input stream for URI: $uri")
                return@withContext
            }
            
            inputStream.use { stream ->
                PDDocument.load(stream).use { document ->
                    val pageCount = document.numberOfPages
                    Log.d(TAG, "PDF loaded, streaming $pageCount pages")
                    
                    val stripper = GeometryPDFStripper()
                    stripper.sortByPosition = true
                    stripper.lineSeparator = "\n"
                    stripper.spacingTolerance = 0.1f // Consistent logic for streaming

                    for (i in 0 until pageCount) {
                        try {
                            stripper.startPage = i + 1
                            stripper.endPage = i + 1
                            val text = stripper.getText(document)
                            onPageExtracted(i, text)
                            Log.d(TAG, "Streamed page $i (${text.length} chars)")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error streaming page $i", e)
                            onPageExtracted(i, "") // Callback with empty on error
                        }
                    }
                }
            }
            Log.d(TAG, "Successfully finished PDF streaming")
            
        } catch (e: IOException) {
            Log.e(TAG, "IOException during PDF streaming", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during PDF streaming", e)
        }
    }

    override suspend fun getPageCount(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        Log.d(TAG, "Getting page count for PDF, URI: $uri")
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Could not open input stream for URI: $uri")
                return@withContext 0
            }
            
            inputStream.use { stream ->
                PDDocument.load(stream).use { document ->
                    val pageCount = document.numberOfPages
                    Log.d(TAG, "PDF has $pageCount pages")
                    pageCount
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "IOException while getting page count: ${e.message}", e)
            e.printStackTrace()
            0
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error getting page count: ${e.javaClass.simpleName} - ${e.message}", e)
            e.printStackTrace()
            0
        }
    }

    /**
     * Custom PDF stripper that is "Layout Aware".
     * It filters out headers and footers based on geometric position (Y-coordinate).
     */
    private class GeometryPDFStripper : PDFTextStripper() {
        
        // Define exclusion zones (percentages of page height)
        // Top 5% and Bottom 5% are usually headers/footers
        private val headerThreshold = 0.05f 
        private val footerThreshold = 0.95f

        override fun processTextPosition(text: com.tom_roush.pdfbox.text.TextPosition) {
            val pageHeight = currentPage.cropBox.height
            val y = text.yDirAdj // Y-coordinate adjusted for direction
            
            val relativeY = y / pageHeight
            
            // Layout Analysis:
            // If text is in the top 5% or bottom 5%, it is likely noise (header/page number).
            // We SKIP processing it entirely.
            if (relativeY < headerThreshold || relativeY > footerThreshold) {
                return
            }
            
            super.processTextPosition(text)
        }
    }
}
