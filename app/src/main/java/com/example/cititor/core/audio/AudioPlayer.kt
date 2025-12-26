package com.example.cititor.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioPlayer {

    private var audioTrack: AudioTrack? = null
    private val sampleRate = 22050 // Default for Piper medium models
    private val channelConfig = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    init {
        createAudioTrack()
    }

    private fun createAudioTrack() {
        try {
            // Increase buffer size to avoid underruns (static/noise)
            // We use 12x the minimum buffer size for maximum stability on all devices
            val optimizedBufferSize = bufferSize * 12
            Log.d("AudioPlayer", "Creating AudioTrack: MinBufferSize=$bufferSize, OptimizedBufferSize=$optimizedBufferSize")

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(optimizedBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to create AudioTrack", e)
        }
    }

    fun play(samples: FloatArray) {
        if (samples.isEmpty()) return
        
        // Convert FloatArray (-1.0 to 1.0) to ShortArray (PCM 16-bit)
        val pcmData = floatArrayToShortArray(samples)
        
        try {
            // Apply a tiny fade-in/out to avoid clicks between segments
            applyFade(pcmData)
            
            // Ensure track is playing before writing
            if (audioTrack?.playState != AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.play()
            }

            // Write in smaller chunks to avoid HAL I/O errors with massive buffers
            val chunkSize = 2048 
            var offset = 0
            while (offset < pcmData.size) {
                // Check if thread is interrupted (job cancelled)
                if (Thread.currentThread().isInterrupted) break

                val sizeToWrite = minOf(chunkSize, pcmData.size - offset)
                val result = audioTrack?.write(pcmData, offset, sizeToWrite) ?: -1
                
                if (result < 0) {
                    Log.e("AudioPlayer", "Write error: $result. Attempting to recover AudioTrack...")
                    recoverAudioTrack()
                    break
                } else if (result == 0) {
                    // Buffer full, wait a bit
                    Thread.sleep(5)
                    continue
                }
                offset += result
            }
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error writing to AudioTrack", e)
        }
    }

    private fun recoverAudioTrack() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
            createAudioTrack()
            Log.d("AudioPlayer", "AudioTrack recovered successfully.")
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to recover AudioTrack", e)
        }
    }

    private fun applyFade(data: ShortArray) {
        val fadeLength = (sampleRate * 0.010).toInt() // 10ms fade for smoother transitions
        if (data.size < fadeLength * 2) return

        for (i in 0 until fadeLength) {
            val factor = i.toFloat() / fadeLength
            data[i] = (data[i] * factor).toInt().toShort()
            data[data.size - 1 - i] = (data[data.size - 1 - i] * factor).toInt().toShort()
        }
    }
    
    fun stop() {
        try {
            audioTrack?.stop()
            audioTrack?.flush()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error stopping AudioTrack", e)
        }
    }

    fun release() {
        try {
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error releasing AudioTrack", e)
        }
    }

    private fun floatArrayToShortArray(floatArray: FloatArray): ShortArray {
        val shortArray = ShortArray(floatArray.size)
        for (i in floatArray.indices) {
            // Clamp value between -1.0 and 1.0
            var v = floatArray[i]
            if (v > 1.0f) v = 1.0f
            if (v < -1.0f) v = -1.0f
            
            // Scale to 16-bit range
            shortArray[i] = (v * 32767).toInt().toShort()
        }
        return shortArray
    }
}
