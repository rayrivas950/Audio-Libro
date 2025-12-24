package com.example.cititor.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.cititor.domain.model.DialogueSegment
import com.example.cititor.domain.model.NarrationSegment
import com.example.cititor.domain.model.TextSegment
import dagger.hilt.android.qualifiers.ApplicationContext
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

    fun speak(segments: List<TextSegment>) {
        if (!isInitialized) return

        _currentSpokenWord.value = null
        
        if (usePiper) {
            scope.launch {
                segments.forEach { segment ->
                    val text = segment.text
                    // TODO: Map VoiceProfile to speakerId and speed
                    val speakerId = 0 
                    val speed = 1.0f
                    
                    // Synthesize
                    val rawAudio = piperTts?.synthesize(text, speed, speakerId)
                    
                    if (rawAudio != null) {
                        // Apply Effects (Pitch, etc.)
                        // val processedAudio = effectProcessor?.applyPitchShift(rawAudio, 22050, 1.0) 
                        
                        // Play
                        audioPlayer?.play(rawAudio)
                    }
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
        if (usePiper) {
            audioPlayer?.stop()
        } else {
            androidTts?.stop()
        }
    }

    fun shutdown() {
        if (usePiper) {
            piperTts?.release()
            audioPlayer?.release()
        } else {
            androidTts?.shutdown()
        }
    }
}
