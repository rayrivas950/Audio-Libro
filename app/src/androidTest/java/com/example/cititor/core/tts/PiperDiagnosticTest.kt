package com.example.cititor.core.tts

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cititor.core.tts.piper.PiperModelConfig
import com.example.cititor.core.tts.piper.PiperTTSEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import android.util.Log
import java.io.File

@RunWith(AndroidJUnit4::class)
class PiperDiagnosticTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun testMiroHighModelInitialization() {
        runBlocking {
            Log.d("PiperTest", "--- Starting Miro High Model Test ---")
            val config = PiperModelConfig(
                assetDir = "piper/vits-piper-es_ES-miro-high",
                modelName = "es_ES-miro-high.onnx",
                configName = "es_ES-miro-high.onnx.json"
            )
            val engine = PiperTTSEngine(context, config)
            
            // Measure memory before
            val runtime = Runtime.getRuntime()
            val memBefore = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            Log.d("PiperTest", "Heap memory before Miro load: ${memBefore}MB")

            try {
                Log.d("PiperTest", "Initializing Miro engine (expecting ~110MB model load)...")
                engine.initialize()
                Log.d("PiperTest", "Miro engine initialized successfully")
                
                val memAfter = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                Log.d("PiperTest", "Heap memory after Miro load: ${memAfter}MB")
                
                val samples = engine.synthesize("Probando voz Miro de alta calidad", speed = 1.0f)
                assertNotNull("Miro synthesis should return samples", samples)
                Log.d("PiperTest", "Miro synthesis successful: ${samples!!.size} samples")
            } catch (e: Exception) {
                Log.e("PiperTest", "Miro model initialization FAILED", e)
                throw e
            } finally {
                engine.release()
            }
        }
    }
    
    @Test
    fun verifyAssetIntegrity() {
        val assetDir = "piper/vits-piper-es_ES-miro-high"
        val assets = context.assets.list(assetDir)
        Log.d("PiperTest", "Files in miro asset dir: ${assets?.joinToString(", ")}")
        assertTrue("Tokens file should exist", assets?.contains("tokens.txt") == true)
        assertTrue("Model file should exist", assets?.contains("es_ES-miro-high.onnx") == true)
        
        // Check size of the onnx file in assets
        try {
            val fd = context.assets.openFd("$assetDir/es_ES-miro-high.onnx")
            Log.d("PiperTest", "Miro model asset size: ${fd.length / (1024*1024)} MB")
            fd.close()
        } catch (e: Exception) {
            Log.w("PiperTest", "Could not check asset size via openFd (might be compressed)", e)
        }
    }
}
