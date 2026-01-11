package com.example.cititor.data.text_extractor

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.cititor.domain.text_extractor.TextExtractor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.contentstream.operator.Operator
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

@Singleton
class PdfExtractor @Inject constructor() : TextExtractor {

    companion object {
        private const val TAG = "PdfExtractor"
    }

    override suspend fun extractText(context: Context, uri: Uri, page: Int): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "Extracting text from PDF page $page, URI: $uri")
        val bookUniqueId = getBookUniqueId(uri)
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
                        val stripper = IndentationAwareStripper(context, bookUniqueId)
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
        val bookUniqueId = getBookUniqueId(uri)
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
                    
                    val stripper = IndentationAwareStripper(context, bookUniqueId)
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

    private fun getBookUniqueId(uri: Uri): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val hash = digest.digest(uri.toString().toByteArray())
            hash.joinToString("") { "%02x".format(it) }.take(8)
        } catch (e: Exception) {
            uri.hashCode().toString()
        }
    }

    /**
     * Custom stripper that detects paragraph indentation.
     */
    /**
     * Custom stripper that detects paragraph indentation and handles page logic.
     */
    private class IndentationAwareStripper(
        private val context: Context,
        private val bookUniqueId: String
    ) : PDFTextStripper() {
        private var minX = Float.MAX_VALUE
        private var indentationThreshold = 15f // Points
        
        // Output Swapping mechanics
        private var originalOutput: java.io.Writer? = null
        private var pageBufferWriter: java.io.StringWriter? = null

        // Image tracking
        private val pageImages = mutableListOf<PdfImageInfo>()
        private var lastProcessedY = -1f

        data class PdfImageInfo(val filename: String, val yTopDown: Float)

        // Guillotine margins
        private val topExclusionMargin = 70f
        private val bottomExclusionMargin = 70f
        
        // Connectors for word counting
        private val connectors = setOf(
            "y", "e", "o", "u", "el", "la", "los", "las", "un", "una", "unos", "unas",
            "de", "del", "a", "al", "en", "por", "para", "con", "sin", "ante", "tras",
            "mi", "tu", "su", "sus", "que"
        )

        override fun startPage(page: com.tom_roush.pdfbox.pdmodel.PDPage) {
            // Hijack the output to buffer the page content
            this.originalOutput = this.output
            this.pageBufferWriter = java.io.StringWriter()
            this.output = this.pageBufferWriter
            
            super.startPage(page)
            minX = Float.MAX_VALUE
            pageImages.clear()
            lastProcessedY = 0f
        }

        override fun processOperator(operator: Operator?, operands: MutableList<COSBase>?) {
            val symbol = operator?.name
            if ("Do" == symbol && operands != null && operands.isNotEmpty()) {
                val name = operands[0] as? COSName
                if (name != null) {
                    val xObject = resources.getXObject(name)
                    if (xObject is PDImageXObject) {
                        val matrix = graphicsState.currentTransformationMatrix
                        val yBottomUp = matrix.translateY
                        val w = matrix.scalingFactorX
                        val h = matrix.scalingFactorY
                        
                        val pageHeight = currentPage.mediaBox.height
                        val yTopDown = pageHeight - yBottomUp - h

                        // Filter tiny noise (icons, lines)
                        if (w > 30 && h > 30) {
                            try {
                                val bitmap = xObject.image
                                val filename = "pdf_${bookUniqueId}_${name.name.hashCode()}_${yTopDown.toInt()}.jpg"
                                
                                val imagesDir = File(context.filesDir, "book_images")
                                if (!imagesDir.exists()) imagesDir.mkdirs()
                                
                                val outputFile = File(imagesDir, filename)
                                if (!outputFile.exists()) {
                                    FileOutputStream(outputFile).use { out ->
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                                    }
                                }
                                
                                pageImages.add(PdfImageInfo(filename, yTopDown))
                                Log.d("PdfExtractor", "Detected PDF image: $filename at Y: $yTopDown")
                            } catch (e: Exception) {
                                Log.e("PdfExtractor", "Error extracting PDF image $name", e)
                            }
                        }
                    }
                }
            }
            super.processOperator(operator, operands)
        }

        override fun writeString(text: String?, textPositions: MutableList<com.tom_roush.pdfbox.text.TextPosition>?) {
            if (textPositions != null && textPositions.isNotEmpty()) {
                val firstChar = textPositions[0]
                val firstCharX = firstChar.xDirAdj
                val firstCharY = firstChar.yDirAdj

                // --- 0. IMAGE INJECTION ---
                // Insert images that appear between the last line and this one
                val pendingImages = pageImages.filter { it.yTopDown >= lastProcessedY && it.yTopDown < firstCharY }
                pendingImages.forEach { img ->
                    output.write("\n\n[IMAGE_REF:${img.filename}]\n\n")
                }
                lastProcessedY = firstCharY
                
                // Get page height for dynamic guillotine
                val pageHeight = currentPage.mediaBox.height
                val topGuillotine = pageHeight * 0.06f
                val bottomGuillotine = pageHeight * 0.06f

                // --- 1. THE GUILLOTINE: Ignore headers and footers ---
                if (firstCharY < topGuillotine) return
                if (firstCharY > pageHeight - bottomGuillotine) return

                // --- 2. MARGIN TRACKING ---
                val pageWidth = currentPage.mediaBox.width
                if (firstCharX < pageWidth * 0.25f && firstCharX < minX) {
                    minX = firstCharX
                }

                // --- 3. PARAGRAPH & TITLE DETECTION ---
                val textWidth = calculateTextWidth(textPositions)
                val trimmedText = text?.trim() ?: ""
                
                // Note: We don't filter noise here line-by-line anymore, 
                // we do it in endPage regex to avoid breaking structure context.

                val isShortLine = textWidth < (pageWidth * 0.5f)
                val endsWithPeriod = trimmedText.endsWith(".")
                val isDialogue = trimmedText.startsWith("—") || trimmedText.startsWith("-")

                // CASE A: Geometrically Centered
                val pageMidpoint = pageWidth / 2
                val midpoint = firstCharX + textWidth / 2
                val diffFromCenter = Math.abs(midpoint - pageMidpoint)
                val isCentered = isShortLine && diffFromCenter < 25f
                
                // CASE B: SEMANTIC RULE
                val significantWordCount = countSignificantWords(trimmedText)
                val isSemanticTitle = isShortLine && !endsWithPeriod

                val isPotentialTitle = isCentered || isSemanticTitle
                val passesFilters = !isDialogue && significantWordCount <= 3 && significantWordCount > 0

                val isIndented = firstCharX > minX + indentationThreshold

                try {
                    if (isPotentialTitle && passesFilters) {
                        output.write("\n\n[GEOMETRIC_TITLE] ")
                    } else if (isIndented) {
                        output.write("\n\n")
                    }
                } catch (e: IOException) {
                    // Ignore write errors to buffer
                }
            }
            super.writeString(text, textPositions)
        }

        override fun endPage(page: com.tom_roush.pdfbox.pdmodel.PDPage) {
            // --- 0. TRAILING IMAGES ---
            // Insert images that appear after the last line of text on the page
            val trailingImages = pageImages.filter { it.yTopDown >= lastProcessedY }
            trailingImages.forEach { img ->
                output.write("\n\n[IMAGE_REF:${img.filename}]\n\n")
            }

            super.endPage(page)
            
            // Restore original output
            this.output = this.originalOutput
            
            // --- 4. POST-PROCESSING: Filter & Density Check ---
            var pageContent = pageBufferWriter?.toString() ?: ""
            
            // A. Title Density Check (Fix for Title Pages/Copyright)
            val titleCount = pageContent.split("[GEOMETRIC_TITLE]").size - 1
            if (titleCount > 2) {
                Log.d("PdfExtractor", "Detected Title Page (Titles: $titleCount). Cleaning markers.")
                pageContent = pageContent.replace("[GEOMETRIC_TITLE] ", "")
            }
            
            // B. Noise Filtering (Page Numbers & Roman/Chapter Numerals)
            // Regex explanations:
            // (?m)^\s*\d+\s*$  -> Lines that are only digits (Page numbers)
            // (?m)^\s*[IVXLCDM]+\s*$ -> Lines that are only Roman numerals (Chapter numbers like I, V, X)
            // We use replace to remove them completely.
            
            // Filter Page Numbers
            pageContent = pageContent.replace(Regex("(?m)^\\s*\\d+\\s*$"), "")
            
            // Filter Roman Numeral Headers (I, II, IV...) if they are the whole line
            // We ensure strict matching to avoid removing words like "DIV" or "MIX".
            pageContent = pageContent.replace(Regex("(?m)^\\s*(?i)[IVXLCDM]+\\s*$"), "")
            
            try {
                output.write(pageContent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }


        private fun countSignificantWords(text: String): Int {
            if (text.isBlank()) return 0
            val words = text.lowercase()
                .replace(Regex("[¡!¿?,.;:()\"]"), "") // Remove common symbols
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
            
            return words.count { it !in connectors }
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
