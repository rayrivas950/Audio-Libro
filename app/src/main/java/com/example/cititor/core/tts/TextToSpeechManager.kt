package com.example.cititor.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.cititor.domain.model.TextSegment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import javax.inject.Inject


class TextToSpeechManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prosodyScriptDao: com.example.cititor.core.database.dao.ProsodyScriptDao
) {

    private var androidTts: TextToSpeech? = null
    private var piperTts: com.example.cititor.core.tts.piper.PiperTTSEngine? = null
    private var audioPlayer: com.example.cititor.core.audio.AudioPlayer? = null
    private var effectProcessor: com.example.cititor.core.audio.AudioEffectProcessor? = null
    private var audioLogger: com.example.cititor.core.debug.AudioLogger? = null
    private val DEBUG_SAVE_AUDIO = false // Set to true to debug
    private var isInitialized = false
    private var usePiper = true // Set to true to use Piper by default
    private var masterSpeed = 0.85f // Reducido un 10% para un ritmo más pausado
    private var masterDramatism = 1.0f // Slider value for "Theater" intensity

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
        // Default model for now (DaveFX Medium)
        val defaultConfig = com.example.cititor.core.tts.piper.PiperModelConfig(
            assetDir = "piper/vits-piper-es_ES-davefx-medium",
            modelName = "es_ES-davefx-medium.onnx",
            configName = "es_ES-davefx-medium.onnx.json"
        )
        
        loadPiperModel(defaultConfig)
        
        // Also prepare Android TTS as immediate fallback
        initAndroidTts()
    }

    private var loadModelJob: Job? = null

    fun switchModel(config: com.example.cititor.core.tts.piper.PiperModelConfig) {
        stop() // Stop playback before switching models
        loadModelJob?.cancel() // Cancel any ongoing loading
        loadModelJob = scope.launch {
            Log.d(TAG, "Switching Piper model to: ${config.assetDir}")
            isInitialized = false
            _isSpeaking.value = false
            Log.d(TAG, "Releasing old Piper engine...")
            piperTts?.release()
            piperTts = null
            
            Log.d(TAG, "Cooling down system memory (500ms)...")
            kotlinx.coroutines.delay(500) 
            System.gc()
            
            Log.d(TAG, "Loading new Piper model: ${config.modelName}")
            loadPiperModel(config)
        }
    }

    private fun loadPiperModel(config: com.example.cititor.core.tts.piper.PiperModelConfig) {
        scope.launch {
            try {
                Log.d(TAG, "Initializing Piper engine with config: ${config.assetDir}")
                val engine = com.example.cititor.core.tts.piper.PiperTTSEngine(context, config)
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
                    lowGainDb = 3.0f,   // +2dB Bass
                    midGainDb = 0.7f,   // +1dB Mids
                    highGainDb = -0.8f // -12dB Treble (Very Dark)
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

    private var audioBufferManager: com.example.cititor.core.audio.AudioBufferManager? = null
    private var playbackJob: Job? = null
    private var synthesisJob: Job? = null

    // ... (rest of class)

    fun speak(bookId: Long, segments: List<TextSegment>, category: com.example.cititor.domain.model.BookCategory = com.example.cititor.domain.model.BookCategory.FICTION, pageIndex: Int = 0) {
        if (!isInitialized) return

        stop() // Stop any ongoing playback
        if (DEBUG_SAVE_AUDIO) {
             // Close previous session if any
             audioLogger?.closeSession()
        }

        _currentSpokenWord.value = null
        _isSpeaking.value = true
        
        // 1. Get native sample rate (assume 22050 if not yet initialized, but piperTts should be ready)
        val nativeRate = piperTts?.getSampleRate() ?: 22050
        val bufferManager = com.example.cititor.core.audio.AudioBufferManager(nativeRate, targetBufferDurationMs = 2000L)
        audioBufferManager = bufferManager

        if (usePiper) {
            // 1. Consumer: Plays audio from the buffer
            playbackJob = scope.launch {
                try {
                    var startTime = System.currentTimeMillis()
                    var chunksPlayed = 0
                    var firstChunk = true
                    
                    Log.d(TAG, "Audio Pipeline Started. Waiting for 2s pre-buffer...")

                    // Double Buffer Logic: Wait until enough audio is ready
                    bufferManager.isReady.first { it }
                    
                    Log.d(TAG, "✅ Pre-buffer ready (${bufferManager.bufferedDurationMs.value}ms). Starting playback.")

                    while (isActive && isSpeaking.value) {
                        val audioData = bufferManager.dequeue()
                        yield() // Ensure cancellation check
                        
                        // REAL-TIME MONITORING
                        val stats = com.example.cititor.core.audio.SpectralAnalyzer.analyze(audioData)
                        
                        if (firstChunk) {
                            val latency = System.currentTimeMillis() - startTime
                            Log.d(TAG, "✅ First Chunk Played. Total Startup Latency: ${latency}ms. Playing immediately.")
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
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        Log.e(TAG, "Playback consumer ended with error", e)
                    }
                } finally {
                    Log.d(TAG, "Playback consumer finished.")
                    if (DEBUG_SAVE_AUDIO) {
                         audioLogger?.closeSession()
                    }
                    if (audioBufferManager === bufferManager) {
                        _isSpeaking.value = false
                        _currentSegment.value = null
                    }
                }
            }

            // 2. Producer: Synthesizes segments and sends to channel
            synthesisJob = scope.launch {
                var sessionStarted = false
                try {
                    segments.forEachIndexed { segIndex, segment ->
                        if (!isActive || !isSpeaking.value) return@forEachIndexed
                        yield() // Check for cancellation

                        val text = segment.text
                         // Update current segment for UI highlighting
                         _currentSegment.value = segment

                         // Phase 35.4: Try to Load DB Script first
                         val dbScript = prosodyScriptDao.getScriptsForPage(bookId, pageIndex)
                             .find { it.segmentIndex == segIndex }
                         
                         val finalSpeed: Float
                         val finalPitch: Float
                         val finalPausePost: Long
                         val finalIntention: com.example.cititor.domain.model.ProsodyIntention

                         if (dbScript != null) {
                             Log.d(TAG, "Using DB Prosody Script for segment $segIndex")
                             finalSpeed = dbScript.speedMultiplier * masterSpeed
                             finalPitch = dbScript.pitchMultiplier
                             finalPausePost = (dbScript.pausePost * masterDramatism).toLong()
                             
                             finalIntention = segment.intention
                         } else {
                             // Fallback to neutral if DB script is missing (unlikely after import)
                             finalSpeed = masterSpeed
                             finalPitch = 1.0f
                             finalPausePost = 200L
                             finalIntention = segment.intention
                         }
                         
                         // Map the speakerId (String) to a Piper Speaker ID (Int)
                         // Narrator is usually 0, and we can assign others modularly.
                         val speakerId = when (segment.speakerId) {
                             "Narrator" -> 0
                             "Character_Generic" -> 1 
                             else -> 0
                         }
                         
                         var rawAudio: FloatArray? = null
                          if (!usePiper) {
                             androidTts?.setSpeechRate(finalSpeed)
                             androidTts?.setPitch(finalPitch)
                             androidTts?.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
                         } else {
                             Log.d(TAG, "Synthesizing segment: '${text.take(30)}...' | Speed: $finalSpeed | Pitch: $finalPitch")
                             
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
                            val targetSpeed = (finalSpeed + jitter).coerceIn(0.5f, 1.5f)
                            
                            // Capped pitch to avoid artifacts (0.5x to 2.0x)
                            val safePitch = finalPitch.coerceIn(0.5f, 2.0f)
                            
                            val compensatedSpeed = targetSpeed / safePitch

                            rawAudio = piperTts?.synthesize(text, compensatedSpeed, speakerId)
                            
                             // 3. Inject Silence Sandwich (Pre-roll + Audio + Post-roll)
                            if (rawAudio != null) {
                                val preRollSamples = (nativeRate * 0.15).toInt() // 150ms Pre
                                
                                val postRollSamples = (nativeRate * (finalPausePost / 1000.0)).toInt()
                                
                                val silencePre = FloatArray(preRollSamples) { 0f }
                                val silencePost = FloatArray(postRollSamples) { 0f }
                                
                                rawAudio = silencePre + rawAudio + silencePost
                            }
                        }
                        
                        if (rawAudio != null) {
                            // [DEBUG] Measure PRE-EQ spectrum
                            val preEqStats = com.example.cititor.core.audio.SpectralAnalyzer.analyze(rawAudio)
                            
                            // Apply modular effects chain with segment context (shouts, whispers, etc)
                            rawAudio = effectProcessor?.applyNaturalization(rawAudio, segment.intention) ?: rawAudio

                            // Apply Pitch Shift if the prosody engine requires it
                            if (finalPitch != 1.0f) {
                                rawAudio = effectProcessor?.applyPitchShift(rawAudio, finalPitch.toDouble()) ?: rawAudio
                            }
                            
                            // [DEBUG] Measure POST-EQ spectrum
                            val postEqStats = com.example.cititor.core.audio.SpectralAnalyzer.analyze(rawAudio)
                            Log.d(TAG, "⚙️ EQ Effect | Treble: %.0f%% → %.0f%% (Δ%.0f%%) | Bass: %.0f%% → %.0f%%".format(
                                preEqStats.trebleEnergy * 100,
                                postEqStats.trebleEnergy * 100,
                                (postEqStats.trebleEnergy - preEqStats.trebleEnergy) * 100,
                                preEqStats.bassEnergy * 100,
                                postEqStats.bassEnergy * 100
                            ))
                            
                            bufferManager.enqueue(rawAudio)
                        }
                    }
                    Log.d(TAG, "🟢 Producer: All segments synthesized.")
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        Log.e(TAG, "🔴 Producer error/crash", e)
                    }
                } finally {
                    Log.d(TAG, "Producer: Finished.")
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

    fun setMasterDramatism(dramatism: Float) {
        this.masterDramatism = dramatism.coerceIn(0.0f, 2.0f)
        Log.d(TAG, "Master dramatism updated to: $masterDramatism")
    }

    fun stop() {
        _isSpeaking.value = false
        playbackJob?.cancel()
        synthesisJob?.cancel()
        
        // Clear the current buffer safely
        audioBufferManager?.clear()
        
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
