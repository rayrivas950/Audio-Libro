package com.example.cititor.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.cititor.domain.model.DialogueSegment
import com.example.cititor.domain.model.NarrationSegment
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
    @ApplicationContext private val context: Context
) {

    private var androidTts: TextToSpeech? = null
    private var piperTts: com.example.cititor.core.tts.piper.PiperTTSEngine? = null
    private var audioPlayer: com.example.cititor.core.audio.AudioPlayer? = null
    private var effectProcessor: com.example.cititor.core.audio.AudioEffectProcessor? = null
    
    private var isInitialized = false
    private var usePiper = false // Disabled by default for stability until fully verified

    private val _currentSpokenWord = MutableStateFlow<IntRange?>(null)
    val currentSpokenWord: StateFlow<IntRange?> = _currentSpokenWord
    
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO)

    companion object {
        private const val TAG = "TTSManager"
    }

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
                effectProcessor = com.example.cititor.core.audio.AudioEffectProcessor()
                
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

    private fun initAndroidTts() {
        androidTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                androidTts?.language = Locale.getDefault()
                isInitialized = true
            }
        }
    }

    private val audioChannel = kotlinx.coroutines.channels.Channel<FloatArray>(capacity = 5)
    private var playbackJob: Job? = null
    private var synthesisJob: Job? = null

    fun speak(segments: List<TextSegment>) {
        if (!isInitialized) return

        stop() // Stop any ongoing playback
        _currentSpokenWord.value = null
        
        if (usePiper) {
            // 1. Consumer: Plays audio from the channel
            playbackJob = scope.launch {
                for (audioData in audioChannel) {
                    audioPlayer?.play(audioData)
                }
            }

            // 2. Producer: Synthesizes segments and sends to channel
            synthesisJob = scope.launch {
                try {
                    segments.forEach { segment ->
                        val text = segment.text
                        val speakerId = 0 
                        val speed = 0.9f
                        
                        Log.d(TAG, "Synthesizing segment: '${text.take(50)}...' at speed $speed")
                        val rawAudio = piperTts?.synthesize(text, speed, speakerId)
                        
                        if (rawAudio != null) {
                            Log.d(TAG, "Synthesis successful. Sending to pipeline. Size: ${rawAudio.size}")
                            audioChannel.send(rawAudio)
                        } else {
                            Log.e(TAG, "Synthesis FAILED for segment")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Producer error", e)
                }
            }
        } else {
            // Fallback to Android TTS
            androidTts?.speak("", TextToSpeech.QUEUE_FLUSH, null, null)
            segments.forEach {
                val utteranceId = UUID.randomUUID().toString()
                androidTts?.speak(it.text, TextToSpeech.QUEUE_ADD, null, utteranceId)
            }
        }
    }

    fun stop() {
        playbackJob?.cancel()
        synthesisJob?.cancel()
        
        // Clear the channel safely
        while (true) {
            val result = audioChannel.tryReceive()
            if (result.isFailure) break
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
