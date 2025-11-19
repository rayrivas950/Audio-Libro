package com.example.audiobook.feature_library.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.example.audiobook.core.common.Resource
import com.tom_rouwe.pdfbox.pdmodel.PDDocument
import com.tom_rouwe.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

class PdfParser @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Extracts text from a specific page of the PDF.
     * @param uri The URI of the PDF file.
     * @param pageIndex The 0-based index of the page to extract.
     */
    suspend fun getPageText(uri: Uri, pageIndex: Int): Resource<String> {
        return withContext(Dispatchers.IO) {
            var document: PDDocument? = null
            try {
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                    ?: return@withContext Resource.Error("No se pudo abrir el archivo.")

                // Load the PDF document
                // Note: For very large files, loading the whole doc might be heavy.
                // Optimization for later: Cache the PDDocument if the user is reading continuously.
                document = PDDocument.load(inputStream)

                if (pageIndex < 0 || pageIndex >= document.numberOfPages) {
                    return@withContext Resource.Error("Página fuera de rango.")
                }

                // Configure the stripper to extract only the requested page
                val stripper = PDFTextStripper()
                stripper.startPage = pageIndex + 1 // PDFBox is 1-based
                stripper.endPage = pageIndex + 1

                val text = stripper.getText(document)
                
                // Basic cleanup: Remove excessive whitespace or weird artifacts if needed
                val cleanText = text.trim()

                if (cleanText.isEmpty()) {
                    Resource.Error("Esta página parece estar vacía o es una imagen escaneada.")
                } else {
                    Resource.Success(cleanText)
                }

            } catch (e: IOException) {
                e.printStackTrace()
                Resource.Error("Error al leer el PDF: ${e.localizedMessage}")
            } catch (e: Exception) {
                e.printStackTrace()
                Resource.Error("Error inesperado: ${e.localizedMessage}")
            } finally {
                document?.close()
            }
        }
    }

    suspend fun generateCoverThumbnail(uri: Uri, bookId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val renderer = PdfRenderer(pfd)
                    if (renderer.pageCount > 0) {
                        val page = renderer.openPage(0)
                        
                        // Create a bitmap for the cover (width: 300px, maintain aspect ratio)
                        val width = 300
                        val height = (width * page.height / page.width)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        
                        // Render the page to the bitmap
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()
                        renderer.close()

                        // Save to internal storage
                        val coversDir = File(context.filesDir, "covers")
                        if (!coversDir.exists()) coversDir.mkdirs()
                        
                        val coverFile = File(coversDir, "cover_$bookId.jpg")
                        FileOutputStream(coverFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                        }
                        
                        return@withContext coverFile.absolutePath
                    }
                }
                null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun getPageCount(uri: Uri): Int {
        return withContext(Dispatchers.IO) {
            var document: PDDocument? = null
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext 0
                document = PDDocument.load(inputStream)
                document.numberOfPages
            } catch (e: Exception) {
                0
            } finally {
                document?.close()
            }
        }
    }
}
