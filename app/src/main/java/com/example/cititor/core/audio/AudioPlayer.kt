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
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            
            audioTrack?.play()
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Failed to create AudioTrack", e)
        }
    }

    fun play(samples: FloatArray) {
        // Convert FloatArray (-1.0 to 1.0) to ShortArray (PCM 16-bit)
        val pcmData = floatArrayToShortArray(samples)
        
        // Write to AudioTrack
        // Note: In STREAM mode, this blocks until data is written.
        // Should be called from a background thread.
        try {
            audioTrack?.write(pcmData, 0, pcmData.size)
        } catch (e: Exception) {
            Log.e("AudioPlayer", "Error writing to AudioTrack", e)
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
