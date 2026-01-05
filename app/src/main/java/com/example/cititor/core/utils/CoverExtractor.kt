package com.example.cititor.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.github.mertakdut.Reader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoverExtractor @Inject constructor() {

    companion object {
        private const val TAG = "CoverExtractor"
        private const val COVER_DIR = "covers"
    }

    suspend fun extractCover(context: Context, uri: Uri, isPdf: Boolean): String? = withContext(Dispatchers.IO) {
        try {
            val coverDir = File(context.filesDir, COVER_DIR)
            if (!coverDir.exists()) {
                coverDir.mkdirs()
            }

            val fileName = "cover_${System.currentTimeMillis()}.jpg"
            val outputFile = File(coverDir, fileName)

            val bitmap = if (isPdf) {
                Log.d(TAG, "Extracting PDF cover for $uri")
                extractPdfFirstPage(context, uri)
            } else {
                Log.d(TAG, "Extracting EPUB cover for $uri")
                extractEpubCover(context, uri)
            }

            if (bitmap != null) {
                saveBitmap(bitmap, outputFile)
                Log.d(TAG, "Cover saved successfully: ${outputFile.name} (${outputFile.length()} bytes)")
                outputFile.absolutePath
            } else {
                Log.w(TAG, "Extraction returned null bitmap for $uri")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting cover: ${e.message}", e)
            null
        }
    }

    private fun extractPdfFirstPage(context: Context, uri: Uri): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r")
            if (pfd != null) {
                renderer = PdfRenderer(pfd)
                if (renderer.pageCount > 0) {
                    val page = renderer.openPage(0)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmap
                } else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering PDF page: ${e.message}")
            null
        } finally {
            renderer?.close()
            pfd?.close()
        }
    }

    private fun extractEpubCover(context: Context, uri: Uri): Bitmap? {
        var tempFile: File? = null
        return try {
            tempFile = File.createTempFile("cover_temp", ".epub", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val reader = Reader()
            reader.setIsIncludingTextContent(false) // Only interested in cover
            reader.setFullContent(tempFile!!.absolutePath)
            
            val coverImage = reader.coverImage
            if (coverImage != null && coverImage.isNotEmpty()) {
                Log.d(TAG, "Cover image found in EPUB, size: ${coverImage.size} bytes")
                android.graphics.BitmapFactory.decodeByteArray(coverImage, 0, coverImage.size)
            } else {
                Log.w(TAG, "Reader.coverImage returned null or empty")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting EPUB cover: ${e.message}")
            null
        } finally {
            tempFile?.delete()
        }
    }

    private fun saveBitmap(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
    }
}
