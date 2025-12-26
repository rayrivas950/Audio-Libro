package com.example.cititor.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

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
            // High stability buffer
            val optimizedBufferSize = bufferSize * 12
            Log.d("AudioPlayer", "Creating AudioTrack: OptimizedBufferSize=$optimizedBufferSize")

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
            // Ensure track is playing
            if (audioTrack?.getState() == AudioTrack.STATE_INITIALIZED && 
                audioTrack?.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack?.play()
            }

            // Write in chunks for stability
            val chunkSize = 2048 
            var offset = 0
            while (offset < pcmData.size) {
                if (Thread.currentThread().isInterrupted) break

                val sizeToWrite = minOf(chunkSize, pcmData.size - offset)
                val result = audioTrack?.write(pcmData, offset, sizeToWrite) ?: -1
                
                if (result < 0) {
                    recoverAudioTrack()
                    break
                } else if (result == 0) {
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
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to recover AudioTrack", e)
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
            val v = floatArray[i].coerceIn(-1.0f, 1.0f)
            shortArray[i] = (v * 32767).toInt().toShort()
        }
        return shortArray
    }
}
