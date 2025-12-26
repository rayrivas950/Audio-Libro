package com.example.cititor.core.debug

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Utility to save raw audio samples to a WAV file for debugging quality issues.
 */
class AudioLogger(private val context: Context) {

    private var fos: FileOutputStream? = null
    private var file: File? = null
    private var totalDataLen = 0
    private var sampleRate = 22050 // Default, updated on start

    fun startSession(rate: Int) {
        try {
            sampleRate = rate
            // Save to app-specific external storage (accessible via Device File Explorer)
            // Path: /sdcard/Android/data/com.example.cititor/files/Documents/debug_synthesis.wav
            val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
            if (dir != null && !dir.exists()) {
                dir.mkdirs()
            }
            file = File(dir, "debug_synthesis.wav")
            
            fos = FileOutputStream(file)
            writeWavHeader(fos!!, 0, totalDataLen + 36, sampleRate.toLong(), 1, 327680) // Placeholder header
            Log.d("AudioLogger", "Recording debug audio to: ${file?.absolutePath}")
        } catch (e: Exception) {
            Log.e("AudioLogger", "Failed to start recording", e)
        }
    }

    fun appendAudio(floatSamples: FloatArray) {
        if (fos == null) return
        
        try {
            val pcmData = ShortArray(floatSamples.size)
            for (i in floatSamples.indices) {
                val v = floatSamples[i].coerceIn(-1.0f, 1.0f)
                pcmData[i] = (v * 32767).toInt().toShort()
            }
            
            val buffer = ByteBuffer.allocate(pcmData.size * 2)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcmData) {
                buffer.putShort(s)
            }
            
            fos?.write(buffer.array())
            totalDataLen += buffer.capacity()
        } catch (e: Exception) {
            Log.e("AudioLogger", "Error appending audio", e)
        }
    }

    fun closeSession() {
        try {
            fos?.close()
            fos = null
            // Update the WAV header with correct length
            updateWavHeader()
            Log.d("AudioLogger", "Debug recording saved. Size: $totalDataLen bytes")
        } catch (e: Exception) {
            Log.e("AudioLogger", "Error closing session", e)
        }
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Int,
        totalDataLen: Int,
        longSampleRate: Long,
        channels: Int,
        byteRate: Int
    ) {
        val header = ByteArray(44)
        
        header[0] = 'R'.code.toByte() // RIFF/WAVE header
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = ((longSampleRate shr 8) and 0xff).toByte()
        header[26] = ((longSampleRate shr 16) and 0xff).toByte()
        header[27] = ((longSampleRate shr 24) and 0xff).toByte()
        
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        
        header[32] = (2 * 16 / 8).toByte() // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()
        
        out.write(header, 0, 44)
    }

    private fun updateWavHeader() {
        try {
            val randomAccessFile = RandomAccessFile(file, "rw")
            randomAccessFile.seek(4) // Write size at offset 4
            randomAccessFile.write(intToByteArray(totalDataLen + 36), 0, 4)
            
            randomAccessFile.seek(40) // Write size at offset 40
            randomAccessFile.write(intToByteArray(totalDataLen), 0, 4)
            
            randomAccessFile.close()
        } catch (e: Exception) {
            Log.e("AudioLogger", "Error updating WAV header", e)
        }
    }
    
    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xff).toByte(),
            ((value shr 8) and 0xff).toByte(),
            ((value shr 16) and 0xff).toByte(),
            ((value shr 24) and 0xff).toByte()
        )
    }
}
