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
                        val stripper = IndentationAwareStripper()
                        stripper.startPage = page + 1
                        stripper.endPage = page + 1
                        stripper.sortByPosition = true
                        stripper.spacingTolerance = 0.3f
                        stripper.lineSeparator = "\n"

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
                    
                    val stripper = IndentationAwareStripper()
                    stripper.sortByPosition = true
                    stripper.spacingTolerance = 0.3f
                    stripper.lineSeparator = "\n"

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

    /**
     * Custom stripper that detects paragraph indentation.
     */
    private class IndentationAwareStripper : PDFTextStripper() {
        private var minX = Float.MAX_VALUE
        private var indentationThreshold = 15f // Points
        private var xCoordinates = mutableListOf<Float>()
        
        // Guillotine margins (in points, 72 points = 1 inch)
        private val topExclusionMargin = 70f
        private val bottomExclusionMargin = 70f

        override fun startPage(page: com.tom_roush.pdfbox.pdmodel.PDPage) {
            super.startPage(page)
            minX = Float.MAX_VALUE
            xCoordinates.clear()
        }

        override fun writeString(text: String?, textPositions: MutableList<com.tom_roush.pdfbox.text.TextPosition>?) {
            if (textPositions != null && textPositions.isNotEmpty()) {
                val firstChar = textPositions[0]
                val firstCharX = firstChar.xDirAdj
                val firstCharY = firstChar.yDirAdj
                
                // Get page height for dynamic guillotine
                val pageHeight = currentPage.mediaBox.height
                val topGuillotine = pageHeight * 0.10f // 10% top
                val bottomGuillotine = pageHeight * 0.075f // 5% bottom

                // --- 1. THE GUILLOTINE: Ignore headers and footers ---
                if (firstCharY < topGuillotine) return
                if (firstCharY > pageHeight - bottomGuillotine) return

                // --- 2. MARGIN TRACKING (minX) ---
                // Only establish margin from lines that are clearly NOT titles (left-ish)
                val pageWidth = currentPage.mediaBox.width
                if (firstCharX < pageWidth * 0.25f && firstCharX < minX) {
                    minX = firstCharX
                }

                    // --- 3. PARAGRAPH & TITLE DETECTION ---
                    val textWidth = calculateTextWidth(textPositions)
                    
                    val trimmedText = text?.trim() ?: ""
                    val isShortLine = textWidth < (pageWidth * 0.5f)
                    val endsWithPeriod = trimmedText.endsWith(".")
                    val isDialogue = trimmedText.startsWith("—") || trimmedText.startsWith("-")

                    // CASE A: Geometrically Centered (very likely a title)
                    val pageMidpoint = pageWidth / 2
                    val midpoint = firstCharX + textWidth / 2
                    val diffFromCenter = Math.abs(midpoint - pageMidpoint)
                    val isCentered = isShortLine && diffFromCenter < 25f
                    
                    // CASE B: SEMANTIC RULE (Proposed by user)
                    // Short line that does NOT end in a period and is NOT a dialogue
                    val isSemanticTitle = isShortLine && !endsWithPeriod && !isDialogue

                    val isIndented = firstCharX > minX + indentationThreshold

                    if (isCentered || isSemanticTitle) {
                        Log.d("IndentationStripper", "Structural Title detected (Semantic): $text")
                        output.write("\n\n[GEOMETRIC_TITLE] ")
                    } else if (isIndented) {
                        output.write("\n\n") // Regular paragraph break
                    }
            }
            super.writeString(text, textPositions)
        }

        private fun calculateTextWidth(positions: List<com.tom_roush.pdfbox.text.TextPosition>): Float {
            if (positions.isEmpty()) return 0f
            val first = positions.first().xDirAdj
            val last = positions.last().let { it.xDirAdj + it.widthDirAdj }
            return last - first
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
            Log.e(TAG, "Unexpected error getting page count: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            0
        }
    }
}
