package com.example.cititor.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.cititor.domain.model.TextSegment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import javax.inject.Inject


class TextToSpeechManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prosodyEngine: com.example.cititor.core.tts.prosody.ProsodyEngine
) {

    private var androidTts: TextToSpeech? = null
    private var piperTts: com.example.cititor.core.tts.piper.PiperTTSEngine? = null
    private var audioPlayer: com.example.cititor.core.audio.AudioPlayer? = null
    private var audioLogger: com.example.cititor.core.debug.AudioLogger? = null
    private val DEBUG_SAVE_AUDIO = true // Set to true to debug audio quality

    init {
        initialize()
    }
    
    private fun initialize() {
        // Start Piper initialization in background (Phase 2.3: Null AssetManager)
        scope.launch {
            try {
                Log.d(TAG, "Phase 2.3: Initializing native Piper engine with null AssetManager...")
                val engine = com.example.cititor.core.tts.piper.PiperTTSEngine(context)
                engine.initialize() 
                
                piperTts = engine
                audioPlayer = com.example.cititor.core.audio.AudioPlayer()
                effectProcessor = com.example.cititor.core.audio.AudioEffectProcessor(
                    effects = listOf()
                )
                
                if (DEBUG_SAVE_AUDIO) {
                    audioLogger = com.example.cititor.core.debug.AudioLogger(context)
                }
                
                usePiper = true // PHASE 3: ENABLE PIPER!
                isInitialized = true
                Log.d(TAG, "Phase 3: Piper TTS ACTIVE and ready for synthesis.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Piper in background, falling back to Android TTS", e)
                usePiper = false
                initAndroidTts()
            }
        }
        
        // Also prepare Android TTS as immediate fallback
        initAndroidTts()
    }

    // ... (rest of class)

    fun speak(segments: List<TextSegment>, category: com.example.cititor.domain.model.BookCategory = com.example.cititor.domain.model.BookCategory.FICTION) {
        if (!isInitialized) return

        stop() // Stop any ongoing playback
        if (DEBUG_SAVE_AUDIO) {
             // Close previous session if any
             audioLogger?.closeSession()
        }

        _currentSpokenWord.value = null
        _isSpeaking.value = true
        
        // ... (channel creation) ...
        val currentChannel = kotlinx.coroutines.channels.Channel<FloatArray>(capacity = 30)
        audioChannel = currentChannel

        if (usePiper) {
            // 1. Consumer: Plays audio from the channel
            playbackJob = scope.launch {
               // ... (consumer logic unchanged) ...
               try {
                    val buffer = mutableListOf<FloatArray>()
                    var prebuffering = true
                    val threshold = 15

                    for (audioData in currentChannel) {
                        if (prebuffering) {
                            buffer.add(audioData)
                            if (buffer.size >= threshold) {
                                Log.d(TAG, "Pre-buffering complete ($threshold segments). Starting playback.")
                                prebuffering = false
                                for (data in buffer) {
                                    audioPlayer?.play(data)
                                    if (DEBUG_SAVE_AUDIO) audioLogger?.appendAudio(data)
                                }
                                buffer.clear()
                            }
                        } else {
                            audioPlayer?.play(audioData)
                            if (DEBUG_SAVE_AUDIO) audioLogger?.appendAudio(audioData)
                        }
                    }
                    
                    // If channel closed before threshold, play remaining
                    if (prebuffering && buffer.isNotEmpty()) {
                        Log.d(TAG, "Channel closed before threshold. Playing remaining ${buffer.size} segments.")
                        for (data in buffer) {
                            audioPlayer?.play(data)
                            if (DEBUG_SAVE_AUDIO) audioLogger?.appendAudio(data)
                        }
                    }
                } finally {
                    Log.d(TAG, "Playback consumer finished.")
                    if (DEBUG_SAVE_AUDIO) {
                         audioLogger?.closeSession()
                    }
                    // Only reset state if this is still the active session
                    if (audioChannel === currentChannel) {
                        _isSpeaking.value = false
                        _currentSegment.value = null
                    }
                }
            }

            // 2. Producer: Synthesizes segments and sends to channel
            synthesisJob = scope.launch {
                var sessionStarted = false
                try {
                    segments.forEachIndexed { index, segment ->
                        if (!isSpeaking.value) return@forEachIndexed
                        // ... (synthesis logic) ...
                        val text = segment.text
                         // Update current segment for UI highlighting
                         _currentSegment.value = segment

                         // Use modular ProsodyEngine to decide how to speak
                         val params = prosodyEngine.calculateParameters(segment, masterSpeed, category)
                         
                         val adjustedSpeed = params.speed ?: masterSpeed
                         val adjustedPitch = params.pitch ?: 1.0f
                         
                         // Map the speakerId (String) to a Piper Speaker ID (Int)
                         // Narrator is usually 0, and we can assign others modularly.
                         val speakerId = when (segment.speakerId) {
                             "Narrator" -> 0
                             "Character_Generic" -> 1 
                             else -> 0
                         }
                         
                         var rawAudio: FloatArray? = null
                         if (!usePiper) {
                             androidTts?.setSpeechRate(adjustedSpeed)
                             androidTts?.setPitch(adjustedPitch)
                             androidTts?.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
                         } else {
                             Log.d(TAG, "Synthesizing segment: '${text.take(30)}...' | Speed: $adjustedSpeed | Pitch: $adjustedPitch")
                             
                             // 1. Get native sample rate from model
                             val nativeRate = piperTts?.getSampleRate() ?: 22050
                             
                             if (!sessionStarted && DEBUG_SAVE_AUDIO) {
                                 audioLogger?.startSession(nativeRate)
                                 sessionStarted = true
                             }

                             // 2. Configure player to match exactly (Bit-Perfect)
                             audioPlayer?.configure(nativeRate)
 
                             rawAudio = piperTts?.synthesize(text, adjustedSpeed, speakerId)
                             
                             // 3. Inject Silence Pre-Roll (100ms) to prevent cut-off words
                             if (rawAudio != null) {
                                 val silenceSamples = (nativeRate * 0.1).toInt() // 100ms
                                 val silence = FloatArray(silenceSamples) { 0f }
                                 rawAudio = silence + rawAudio!!
                             }
                         }
                        
                        if (rawAudio != null) {
                            // Apply modular effects chain with segment context (shouts, whispers, etc)
                            rawAudio = effectProcessor?.applyNaturalization(rawAudio, segment.intention) ?: rawAudio

                            // Apply Pitch Shift if the prosody engine requires it
                            if (adjustedPitch != 1.0f) {
                                val sampleRate = piperTts?.getSampleRate() ?: 22050
                                rawAudio = effectProcessor?.applyPitchShift(rawAudio, sampleRate, adjustedPitch.toDouble()) ?: rawAudio
                            }
                            
                            currentChannel.send(rawAudio)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Producer error", e)
                } finally {
                    currentChannel.close()
                }
            }
        } else {
             // Fallback logic ...
             androidTts?.speak("", TextToSpeech.QUEUE_FLUSH, null, null)
             segments.forEach {
                 val utteranceId = java.util.UUID.randomUUID().toString()
                 androidTts?.speak(it.text, TextToSpeech.QUEUE_ADD, null, utteranceId)
             }
        }
    }

    fun setMasterSpeed(speed: Float) {
        this.masterSpeed = speed.coerceIn(0.1f, 2.0f)
        Log.d(TAG, "Master speed updated to: $masterSpeed")
    }

    fun stop() {
        _isSpeaking.value = false
        playbackJob?.cancel()
        synthesisJob?.cancel()
        
        // Clear the current channel safely
        val channelToClear = audioChannel
        while (true) {
            try {
                val result = channelToClear.tryReceive()
                if (result.isFailure) break
            } catch (e: Exception) {
                break
            }
        }
        
        if (usePiper) {
            audioPlayer?.stop()
        } else {
            androidTts?.stop()
        }
    }

    fun shutdown() {
        stop()
        if (usePiper) {
            piperTts?.release()
            audioPlayer?.release()
        } else {
            androidTts?.shutdown()
        }
    }
}
