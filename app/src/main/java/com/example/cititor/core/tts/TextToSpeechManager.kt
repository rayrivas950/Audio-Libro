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
    private var effectProcessor: com.example.cititor.core.audio.AudioEffectProcessor? = null
    
    private var isInitialized = false
    private var usePiper = true // Set to true to use Piper by default
    private var masterSpeed = 0.94f // Incrementado un 10% (de 0.85 a 0.94) para una narración más ágil

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private val _currentSegment = MutableStateFlow<TextSegment?>(null)
    val currentSegment: StateFlow<TextSegment?> = _currentSegment

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
                effectProcessor = com.example.cititor.core.audio.AudioEffectProcessor(
                    effects = listOf(
                        // Add effects here modularly, e.g.:
                        // com.example.cititor.core.audio.effects.NormalizationEffect(0.7f),
                        // com.example.cititor.core.audio.effects.HighCutFilterEffect(7500f)
                    )
                )
                
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

    private var audioChannel = kotlinx.coroutines.channels.Channel<FloatArray>(capacity = 5)
    private var playbackJob: Job? = null
    private var synthesisJob: Job? = null

    fun speak(segments: List<TextSegment>, category: com.example.cititor.domain.model.BookCategory = com.example.cititor.domain.model.BookCategory.FICTION) {
        if (!isInitialized) return

        stop() // Stop any ongoing playback
        _currentSpokenWord.value = null
        _isSpeaking.value = true
        
        // Recreate channel for each session with higher capacity for lookahead
        val currentChannel = kotlinx.coroutines.channels.Channel<FloatArray>(capacity = 30)
        audioChannel = currentChannel

        if (usePiper) {
            // 1. Consumer: Plays audio from the channel
            playbackJob = scope.launch {
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
                                }
                                buffer.clear()
                            }
                        } else {
                            audioPlayer?.play(audioData)
                        }
                    }
                    
                    // If channel closed before threshold, play remaining
                    if (prebuffering && buffer.isNotEmpty()) {
                        Log.d(TAG, "Channel closed before threshold. Playing remaining ${buffer.size} segments.")
                        for (data in buffer) {
                            audioPlayer?.play(data)
                        }
                    }
                } finally {
                    Log.d(TAG, "Playback consumer finished.")
                    // Only reset state if this is still the active session
                    if (audioChannel === currentChannel) {
                        _isSpeaking.value = false
                        _currentSegment.value = null
                    }
                }
            }

            // 2. Producer: Synthesizes segments and sends to channel
            synthesisJob = scope.launch {
                try {
                    segments.forEachIndexed { index, segment ->
                        if (!isSpeaking.value) return@forEachIndexed
                        
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
            // Fallback to Android TTS
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
