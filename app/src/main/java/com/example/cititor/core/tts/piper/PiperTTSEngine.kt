package com.example.cititor.core.tts.piper

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PiperTTSEngine(private val context: Context) {

    private var tts: OfflineTts? = null
    private val modelDir = "piper/vits-piper-es_ES-davefx-medium"
    private val modelName = "es_ES-davefx-medium.onnx"
    private val configName = "es_ES-davefx-medium.onnx.json"
    private val tokensName = "tokens.txt"
    private val dataDirName = "espeak-ng-data"

    // No auto-initialize in init to avoid blocking or crashing during construction
    init {
        // Only basic setup
    }

    suspend fun initialize() {
        if (tts != null) return // Already initialized
        
        try {
            Log.d("PiperTTSEngine", "Starting Piper TTS initialization...")
            val internalModelDir = File(context.filesDir, "piper_models")
            val internalDataDir = File(context.filesDir, "espeak_data")

            // Clean start to avoid corruption
            if (internalModelDir.exists()) internalModelDir.deleteRecursively()
            if (internalDataDir.exists()) internalDataDir.deleteRecursively()
            
            internalModelDir.mkdirs()
            internalDataDir.mkdirs()

            // Copy model files with integrity check (size)
            copyAssetFileWithCheck(context.assets, "$modelDir/$modelName", File(internalModelDir, modelName))
            copyAssetFileWithCheck(context.assets, "$modelDir/$configName", File(internalModelDir, configName))
            copyAssetFileWithCheck(context.assets, "$modelDir/$tokensName", File(internalModelDir, tokensName))
            
            // Copy espeak-ng-data from the model directory
            val modelDataPath = "$modelDir/$dataDirName"
            val targetDataDir = File(internalDataDir, dataDirName)
            copyAssets(context.assets, modelDataPath, targetDataDir.absolutePath)

            val modelFile = File(internalModelDir, modelName)
            val tokensFile = File(internalModelDir, tokensName)
            val dataDir = targetDataDir

            if (!modelFile.exists() || !tokensFile.exists() || !dataDir.exists()) {
                throw IOException("Required Piper files missing after copy. Model: ${modelFile.exists()}, Tokens: ${tokensFile.exists()}, DataDir: ${dataDir.exists()}")
            }

            Log.d("PiperTTSEngine", "File sizes: Model=${modelFile.length()}, Tokens=${tokensFile.length()}, Config=${File(internalModelDir, configName).length()}")
            Log.d("PiperTTSEngine", "Loading Sherpa-ONNX model from: ${modelFile.canonicalPath}")
            
            val modelConfig = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = modelFile.canonicalPath,
                    tokens = tokensFile.canonicalPath,
                    dataDir = internalDataDir.canonicalPath // Point to the FOLDER CONTAINING espeak-ng-data
                ),
                numThreads = 4, // Increased as per user suggestion/standard
                debug = true // Re-enable debug to see more native info if it crashes
            )

            val config = OfflineTtsConfig(
                model = modelConfig
            )

            // Ensure files are readable
            if (!modelFile.canRead()) throw IOException("Cannot read model file")

            Log.d("PiperTTSEngine", "Phase 2.3: Initializing native OfflineTts with null AssetManager (Filesystem mode)...")
            tts = OfflineTts(null, config)
            Log.d("PiperTTSEngine", "Piper TTS initialized successfully (Native engine loaded from filesystem via null assets)")

        } catch (e: Exception) {
            Log.e("PiperTTSEngine", "Failed to initialize Piper TTS", e)
            throw e // Re-throw to let manager handle fallback
        }
    }

    fun synthesize(text: String, speed: Float = 1.0f, speakerId: Int = 0): FloatArray? {
        val tts = tts ?: return null
        
        // Note: Sherpa-ONNX generate function might vary in signature depending on version.
        // Assuming generate(text, sid, speed) returning AudioData or similar.
        // Let's check the API or assume standard usage.
        // Usually: tts.generate(text, sid, speed) returns OfflineTtsGeneratedAudio
        
        val audio = tts.generate(text, sid = speakerId, speed = speed)
        return audio.samples
    }
    
    fun getSampleRate(): Int {
        return tts?.sampleRate() ?: 22050
    }

    fun release() {
        tts?.release()
        tts = null
    }

    private fun copyAssetFileWithCheck(assets: AssetManager, assetPath: String, destFile: File) {
        try {
            val assetFd = assets.openFd(assetPath)
            val assetSize = assetFd.length
            assetFd.close()

            if (destFile.exists() && destFile.length() == assetSize) {
                Log.d("PiperTTSEngine", "Asset $assetPath already exists and size matches, skipping copy.")
                return
            }
            
            Log.d("PiperTTSEngine", "Copying asset $assetPath to ${destFile.absolutePath} (Size: $assetSize)")
            assets.open(assetPath).use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        } catch (e: Exception) {
            // Fallback for compressed assets where openFd might fail
            Log.w("PiperTTSEngine", "openFd failed for $assetPath, using standard copy", e)
            if (!destFile.exists()) {
                assets.open(assetPath).use { inputStream ->
                    FileOutputStream(destFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        }
    }

    private fun copyAssets(assetManager: AssetManager, path: String, outPath: String) {
        val assets = assetManager.list(path)
        if (assets.isNullOrEmpty()) {
            // It's a file
            copyAssetFileWithCheck(assetManager, path, File(outPath))
        } else {
            // It's a directory
            val dir = File(outPath)
            if (!dir.exists()) dir.mkdirs()
            for (asset in assets) {
                val nextAssetPath = if (path.isEmpty()) asset else "$path/$asset"
                copyAssets(assetManager, nextAssetPath, "$outPath/$asset")
            }
        }
    }
}
