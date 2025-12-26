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

    private var tailBuffer: ShortArray? = null
    private val crossfadeLength = (sampleRate * 0.020).toInt() // 20ms crossfade

    private var isPrimed = false

    fun play(samples: FloatArray) {
        if (samples.isEmpty()) return
        
        // Convert FloatArray (-1.0 to 1.0) to ShortArray (PCM 16-bit)
        var pcmData = floatArrayToShortArray(samples)
        
        try {
            // 1. Apply Room Tone (Subtle noise to avoid absolute silence)
            // Reduced to -70dB (range +/- 16) for a cleaner start
            applyRoomTone(pcmData)

            // 2. Apply Exponential Fade
            applyExponentialFade(pcmData)
            
            // 3. Apply Crossfade with previous segment
            val finalData = applyCrossfade(pcmData)

            // 4. Hardware Priming: Write subtle room tone before first data to stabilize driver
            if (!isPrimed) {
                val primingSize = (sampleRate * 0.100).toInt() // 100ms priming
                val primingData = ShortArray(primingSize)
                applyRoomTone(primingData) // Warm up the noise integrator
                audioTrack?.write(primingData, 0, primingData.size)
                isPrimed = true
            }

            // 5. Deferred Start: Ensure track is playing. 
            // Only call play() if not already in that state to avoid driver-level glitches/pops.
            if (audioTrack?.getState() == AudioTrack.STATE_INITIALIZED && 
                audioTrack?.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                Log.d("AudioPlayer", "Starting AudioTrack playback...")
                audioTrack?.play()
            }

            // Write in smaller chunks to avoid HAL I/O errors with massive buffers
            val chunkSize = 2048 
            var offset = 0
            while (offset < finalData.size) {
                // Check if thread is interrupted (job cancelled)
                if (Thread.currentThread().isInterrupted) break

                val sizeToWrite = minOf(chunkSize, finalData.size - offset)
                val result = audioTrack?.write(finalData, offset, sizeToWrite) ?: -1
                
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

    private var lastRoomToneValue = 0f

    private fun applyRoomTone(data: ShortArray) {
        val random = java.util.Random()
        for (i in data.indices) {
            // Brownian Noise: Integration of white noise creates a warmer, "brown" sound
            // Range +/- 8 for white noise, then integrated with a leaky factor
            val white = (random.nextFloat() * 16f - 8f)
            lastRoomToneValue = 0.98f * lastRoomToneValue + white
            
            // Coerce to keep it in a safe, very quiet range (~ -70dB)
            val noise = lastRoomToneValue.toInt().toShort()
            data[i] = (data[i] + noise).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun applyExponentialFade(data: ShortArray) {
        val fadeLength = (sampleRate * 0.015).toInt() // 15ms fade
        if (data.size < fadeLength * 2) return

        for (i in 0 until fadeLength) {
            val factor = i.toFloat() / fadeLength
            val expFactor = factor * factor // Exponential curve y = x^2
            
            // Fade-in
            data[i] = (data[i] * expFactor).toInt().toShort()
            
            // Fade-out (at the end of the buffer)
            val j = data.size - 1 - i
            data[j] = (data[j] * expFactor).toInt().toShort()
        }
    }

    private fun applyCrossfade(newData: ShortArray): ShortArray {
        val tail = tailBuffer
        if (tail == null) {
            // First segment, just save the tail and return
            tailBuffer = newData.takeLast(crossfadeLength).toShortArray()
            return newData
        }

        // Mix the tail of the previous segment with the start of the new one
        val mixedData = newData.copyOf()
        for (i in 0 until minOf(crossfadeLength, mixedData.size)) {
            val factor = i.toFloat() / crossfadeLength
            // Linear mix for crossfade
            mixedData[i] = (tail[i] * (1f - factor) + newData[i] * factor).toInt().toShort()
        }

        // Update tail buffer for next segment
        tailBuffer = newData.takeLast(crossfadeLength).toShortArray()
        
        return mixedData
    }

    private fun recoverAudioTrack() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
            tailBuffer = null // Reset crossfade on recovery
            isPrimed = false // Reset priming on recovery
            lastRoomToneValue = 0f // Reset noise integrator
            createAudioTrack()
            Log.d("AudioPlayer", "AudioTrack recovered successfully.")
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to recover AudioTrack", e)
        }
    }
    
    fun stop() {
        try {
            audioTrack?.stop()
            audioTrack?.flush()
            isPrimed = false // Reset priming for next session
            tailBuffer = null // Reset crossfade for next session
            lastRoomToneValue = 0f // Reset noise integrator
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
