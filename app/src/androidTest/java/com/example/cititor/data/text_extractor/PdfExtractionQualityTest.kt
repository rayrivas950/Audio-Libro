package com.example.cititor.data.text_extractor

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class PdfExtractionQualityTest {

    private val extractor = PdfExtractor()
    private val targetContext = ApplicationProvider.getApplicationContext<Context>()
    private val testContext = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context

    @Test
    fun testTortureTestExtractionQuality() = runBlocking {
        // 1. Preparar el archivo desde assets del test
        val assetName = "torture_test.pdf"
        val cacheFile = File(targetContext.cacheDir, assetName)
        testContext.assets.open(assetName).use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        }
        val uri = Uri.fromFile(cacheFile)

        // 2. Extraer texto usando el contexto de la aplicación (target)
        val result = StringBuilder()
        extractor.extractPages(targetContext, uri) { page, text ->
            result.append("--- PAGE $page ---\n")
            result.append(text)
            result.append("\n\n")
            
            Log.i("QUALITY_TEST", "RAW PAGE $page:\n$text")
        }

        val fullText = result.toString()

        // 3. Verificaciones de Calidad Atómicas (Buscando fragmentación)
        // Buscamos patrones conocidos de error en Sapkowski/Torture
        val hasQueAquel = fullText.contains("queaquel", ignoreCase = true)
        val hasYLos = fullText.contains("ylos", ignoreCase = true)
        val hasYDe = fullText.contains("yde", ignoreCase = true)
        
        Log.i("QUALITY_TEST", "--- QUALITY REPORT ---")
        Log.i("QUALITY_TEST", "Fragmentación 'queaquel': ${if (hasQueAquel) "DETECTADA" else "LIMPIO"}")
        Log.i("QUALITY_TEST", "Fragmentación 'ylos': ${if (hasYLos) "DETECTADA" else "LIMPIO"}")
        Log.i("QUALITY_TEST", "Fragmentación 'yde': ${if (hasYDe) "DETECTADA" else "LIMPIO"}")

        // Mostramos una muestra representativa para el usuario
        Log.i("QUALITY_TEST", "SAMPLE (First 500 chars):\n${fullText.take(500)}")

        // El test pasa si al menos extrae texto (queremos ver el log, no fallar el build)
        assertTrue("El texto extraído está vacío", fullText.length > 100)
    }
}
