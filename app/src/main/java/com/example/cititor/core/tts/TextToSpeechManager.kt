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
    private var audioLogger: com.example.cititor.core.debug.AudioLogger? = null
    private val DEBUG_SAVE_AUDIO = false // Set to true to debug audio quality

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
        // Initialize Audio Effect Processor with Master Chain
        effectProcessor = com.example.cititor.core.audio.AudioEffectProcessor(
            effects = listOf(
                // 3-Band Parametric EQ
                // Target: Treble < 12% (Massive High Cut)
                // Bass boost for "Geralt" body
                com.example.cititor.core.audio.effects.ThreeBandEqEffect(
                    lowGainDb = 2.0f,   // +2dB Bass
                    midGainDb = 1.0f,   // +1dB Mids
                    highGainDb = -12.0f // -12dB Treble (Very Dark)
                )
            )
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
                    var startTime = System.currentTimeMillis()
                    var chunksPlayed = 0
                    var firstChunk = true
                    
                    Log.d(TAG, "Audio Pipeline Started. Streaming directly...")

                    for (audioData in currentChannel) {
                        // REAL-TIME MONITORING
                        val stats = com.example.cititor.core.audio.SpectralAnalyzer.analyze(audioData)
                        
                        if (firstChunk) {
                            val latency = System.currentTimeMillis() - startTime
                            Log.d(TAG, "✅ First Chunk Received. Latency: ${latency}ms. Size: ${audioData.size}. Playing immediately.")
                            firstChunk = false
                        }
                        
                        Log.d(TAG, "🔊 Spectrum | Bass: %.0f%% | Mid: %.0f%% | Treble: %.0f%% [%s]".format(
                            stats.bassEnergy * 100, 
                            stats.midEnergy * 100, 
                            stats.trebleEnergy * 100,
                            stats.dominance
                        ))
                        
                        audioPlayer?.play(audioData)
                        chunksPlayed++
                        if (DEBUG_SAVE_AUDIO) audioLogger?.appendAudio(audioData)
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
                            
                            // PHYSICS TRICK: Decouple Speed from Pitch
                            // If we pitch shift by P, the audio duration changes by 1/P (speed changes by P).
                            // To get final speed S, we must generate at G such that G * P = S.
                            // So, G = S / P.
                            
                            // Humanize: Add slight random speed variation (+/- 4%)
                            val jitter = kotlin.random.Random.nextDouble(-0.04, 0.04).toFloat()
                            val targetSpeed = (adjustedSpeed + jitter).coerceIn(0.5f, 1.5f)
                            
                            // Capped pitch to avoid artifacts (0.5x to 2.0x)
                            val safePitch = adjustedPitch.coerceIn(0.5f, 2.0f)
                            
                            val compensatedSpeed = targetSpeed / safePitch

                            rawAudio = piperTts?.synthesize(text, compensatedSpeed, speakerId)
                            
                            // 3. Inject Silence Sandwich (Pre-roll + Audio + Post-roll)
                            if (rawAudio != null) {
                                val preRollSamples = (nativeRate * 0.15).toInt() // 150ms Pre
                                
                                // Dynamic Post-Roll Logic
                                // Priority 1: Explicit Pause from Prosody Engine (e.g. Titles)
                                // Priority 2: Intention-based heuristic
                                val postRollDurationMs = if (params.pausePost != null) {
                                    params.pausePost
                                } else {
                                    when (segment.intention) {
                                        com.example.cititor.domain.model.ProsodyIntention.SHOUT,
                                        com.example.cititor.domain.model.ProsodyIntention.ADRENALINE,
                                        com.example.cititor.domain.model.ProsodyIntention.PAIN -> 250L
                                        com.example.cititor.domain.model.ProsodyIntention.EMPHASIS -> 300L
                                        else -> 200L
                                    }
                                }
                                
                                val postRollSamples = (nativeRate * (postRollDurationMs / 1000.0)).toInt()
                                
                                val silencePre = FloatArray(preRollSamples) { 0f }
                                val silencePost = FloatArray(postRollSamples) { 0f }
                                
                                rawAudio = silencePre + rawAudio!! + silencePost
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
                    Log.d(TAG, "🟢 Producer: All segments synthesized. Closing channel.")
                } catch (e: Exception) {
                    Log.e(TAG, "🔴 Producer error/crash", e)
                } finally {
                    Log.d(TAG, "Producer: Closing channel finally.")
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
