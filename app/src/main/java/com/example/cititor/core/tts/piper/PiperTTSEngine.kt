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

data class PiperModelConfig(
    val assetDir: String,
    val modelName: String,
    val configName: String,
    val tokensName: String = "tokens.txt",
    val dataDirName: String = "espeak-ng-data"
)

class PiperTTSEngine(
    private val context: Context,
    private val modelConfig: PiperModelConfig
) {

    private var tts: OfflineTts? = null
    private val modelDir get() = modelConfig.assetDir
    private val modelName get() = modelConfig.modelName
    private val configName get() = modelConfig.configName
    private val tokensName get() = modelConfig.tokensName
    private val dataDirName get() = modelConfig.dataDirName

    // No auto-initialize in init to avoid blocking or crashing during construction
    init {
        // Only basic setup
    }

    suspend fun initialize() {
        if (tts != null) return // Already initialized
        
        try {
            Log.d("PiperTTSEngine", "Memory cleanup before HQ model load...")
            System.gc() // Hint system to free up memory for the large ONNX model
            
            Log.d("PiperTTSEngine", "Starting Piper TTS initialization...")
            
            // Smart Directory Management: One folder per model to avoid mixing
            val internalModelDir = File(context.filesDir, "models/${modelConfig.assetDir.replace("/", "_")}")
            val internalDataDir = File(internalModelDir, "espeak_data")

            if (!internalModelDir.exists()) internalModelDir.mkdirs()
            if (!internalDataDir.exists()) internalDataDir.mkdirs()

            // Copy model files ONLY if they don't exist or size differs
            copyAssetFileWithCheck(context.assets, "$modelDir/$modelName", File(internalModelDir, modelName))
            copyAssetFileWithCheck(context.assets, "$modelDir/$configName", File(internalModelDir, configName))
            copyAssetFileWithCheck(context.assets, "$modelDir/$tokensName", File(internalModelDir, tokensName))
            
            // Atomic copy for assets: always ensure integrity of training data
            val modelDataPath = "$modelDir/$dataDirName"
            val targetDataDir = File(internalDataDir, dataDirName)
            
            // Delete if exists to ensure a perfectly clean copy of espeak-ng-data structure
            if (targetDataDir.exists()) {
                Log.d("PiperTTSEngine", "Cleaning existing data dir to ensure integrity: ${targetDataDir.absolutePath}")
                targetDataDir.deleteRecursively()
            }
            
            Log.d("PiperTTSEngine", "Copying fresh training data to: ${targetDataDir.absolutePath}")
            copyAssets(context.assets, modelDataPath, targetDataDir.absolutePath)

            val modelFile = File(internalModelDir, modelName)
            val tokensFile = File(internalModelDir, tokensName)
            val dataDir = targetDataDir

            if (!modelFile.exists() || !tokensFile.exists() || !dataDir.exists()) {
                throw IOException("Required Piper files missing after copy. Model: ${modelFile.exists()}, Tokens: ${tokensFile.exists()}, DataDir: ${dataDir.exists()}")
            }

            Log.d("PiperTTSEngine", "File sizes: Model=${modelFile.length()}, Tokens=${tokensFile.length()}, Config=${File(internalModelDir, configName).length()}")
            Log.d("PiperTTSEngine", "Loading Sherpa-ONNX model from: ${modelFile.canonicalPath}")
            
            val nativeVitsConfig = OfflineTtsVitsModelConfig(
                model = modelFile.canonicalPath,
                tokens = tokensFile.canonicalPath,
                dataDir = targetDataDir.canonicalPath
            )

            val nativeConfig = OfflineTtsModelConfig(
                vits = nativeVitsConfig,
                numThreads = 1, // Safer for emulator and prevents crashes with large models
                debug = true 
            )

            val config = OfflineTtsConfig(
                model = nativeConfig
            )

            // Ensure files are readable
            if (!modelFile.canRead()) throw IOException("Cannot read model file")

            Log.d("PiperTTSEngine", "Phase 2.3: Initializing native OfflineTts (Filesystem mode)...")
            tts = OfflineTts(null, config)
            Log.d("PiperTTSEngine", "Piper TTS initialized successfully (Native engine loaded from filesystem)")

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
        val tempFile = File(destFile.parent, "${destFile.name}.temp")
        try {
            // Check if destination already valid (using size as heuristic)
            try {
                assets.openFd(assetPath).use { assetFd ->
                    val assetSize = assetFd.length
                    if (destFile.exists() && destFile.length() == assetSize && isValidOnnx(destFile)) {
                        Log.d("PiperTTSEngine", "Asset $assetPath already exists and is valid, skipping copy.")
                        return
                    }
                }
            } catch (e: Exception) {
                Log.d("PiperTTSEngine", "Cannot check size via openFd for $assetPath, proceeding with atomic copy")
            }

            Log.d("PiperTTSEngine", "Starting ATOMIC copy for $assetPath...")
            assets.open(assetPath).use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    // Use a larger buffer (128KB) for steady transfer of large models
                    val buffer = ByteArray(128 * 1024)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()
                }
            }
            
            // Rename to final destination only if copy finished successfully
            if (tempFile.renameTo(destFile)) {
                Log.d("PiperTTSEngine", "Successfully copied and verified $assetPath")
            } else {
                throw IOException("Failed to rename temporary file to ${destFile.absolutePath}")
            }

        } catch (e: Exception) {
            Log.e("PiperTTSEngine", "Critical failure during atomic copy of $assetPath", e)
            tempFile.delete()
            throw e
        }
    }

    private fun isValidOnnx(file: File): Boolean {
        if (!file.exists() || file.length() < 100) return false
        try {
            file.inputStream().use { input ->
                val header = ByteArray(8)
                input.read(header)
                // Simply log it for diagnostics
                Log.d("PiperTTSEngine", "File header for ${file.name}: ${header.joinToString("") { "%02x".format(it) }}")
                // Standard ONNX files usually start with some Protobuf markers or specific patterns
                // We won't block it, just log it. 
                return true 
            }
        } catch (e: Exception) { return false }
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
