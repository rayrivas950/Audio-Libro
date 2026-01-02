package com.example.cititor.core.audio

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages an audio chunk queue with time-based monitoring.
 * Facilitates "Double Buffering" by ensuring a target look-ahead duration.
 */
class AudioBufferManager(
    private val sampleRate: Int,
    private val targetBufferDurationMs: Long = 2000L
) {
    private val channel = Channel<FloatArray>(capacity = Channel.UNLIMITED)
    private var totalSamplesBuffered: Long = 0
    
    private val _bufferedDurationMs = MutableStateFlow(0L)
    val bufferedDurationMs: StateFlow<Long> = _bufferedDurationMs

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    /**
     * Adds a chunk to the buffer and updates duration metrics.
     */
    suspend fun enqueue(samples: FloatArray) {
        channel.send(samples)
        totalSamplesBuffered += samples.size
        
        val durationMs = (totalSamplesBuffered * 1000L) / sampleRate
        _bufferedDurationMs.value = durationMs
        
        if (durationMs >= targetBufferDurationMs) {
            _isReady.value = true
        }
    }

    /**
     * Retrieves the next chunk for playback.
     */
    suspend fun dequeue(): FloatArray {
        val samples = channel.receive()
        totalSamplesBuffered -= samples.size
        
        val durationMs = (totalSamplesBuffered * 1000L) / sampleRate
        _bufferedDurationMs.value = durationMs
        
        return samples
    }

    /**
     * Clears all buffered audio.
     */
    fun clear() {
        totalSamplesBuffered = 0
        _bufferedDurationMs.value = 0
        _isReady.value = false
        // Channel doesn't have a clear(), so we'll rely on closing/recreating 
        // in the manager if needed, or draining it.
    }

    fun getInternalChannel(): Channel<FloatArray> = channel
}
