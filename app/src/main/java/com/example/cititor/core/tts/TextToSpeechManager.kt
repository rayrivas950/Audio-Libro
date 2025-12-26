package com.example.cititor.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.cititor.domain.model.DialogueSegment
import com.example.cititor.domain.model.Emotion
import com.example.cititor.domain.model.NarrationSegment
import com.example.cititor.domain.model.NarrationStyle
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
    private var usePiper = true // Set to true to use Piper by default
    private var masterSpeed = 0.85f // Subido de 0.75 a 0.85 para evitar "estiramiento"

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

    private var audioChannel = kotlinx.coroutines.channels.Channel<FloatArray>(capacity = 5)
    private var playbackJob: Job? = null
    private var synthesisJob: Job? = null

    fun speak(segments: List<TextSegment>, bookCharacters: List<com.example.cititor.domain.model.Character> = emptyList(), properNames: Set<String> = emptySet()) {
        if (!isInitialized) return

        stop() // Stop any ongoing playback
        _currentSpokenWord.value = null
        _isSpeaking.value = true
        consecutiveCommas = 0
        
        // Recreate channel for each session to avoid ClosedSendChannelException from previous cancelled jobs
        val currentChannel = kotlinx.coroutines.channels.Channel<FloatArray>(capacity = 5)
        audioChannel = currentChannel

        if (usePiper) {
            // 1. Consumer: Plays audio from the channel
            playbackJob = scope.launch {
                try {
                    for (audioData in currentChannel) {
                        audioPlayer?.play(audioData)
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
                    var lastSpeakerId: String? = null
                    
                    segments.forEachIndexed { index, segment ->
                        if (!isSpeaking.value) return@forEachIndexed
                        
                        val text = segment.text
                        val nextSegment = segments.getOrNull(index + 1)
                        
                        // Update current segment for UI highlighting
                        _currentSegment.value = segment
                        
                        // Detect Speaker Change
                        val currentSpeakerId = if (segment is DialogueSegment) segment.speakerId else "NARRATOR"
                        if (lastSpeakerId != null && lastSpeakerId != currentSpeakerId) {
                            Log.d(TAG, "Speaker change detected: $lastSpeakerId -> $currentSpeakerId. Inserting transition pause.")
                            val sampleRate = piperTts?.getSampleRate() ?: 22050
                            val transitionSilence = generateSilence(400L, sampleRate)
                            audioChannel.send(transitionSilence)
                        }
                        lastSpeakerId = currentSpeakerId

                        // Default parameters (Using Master Speed)
                        var speed = masterSpeed
                        var pitch = 1.0f
                        var speakerId = 0
                        
                        // 1. Resolve Voice Profile
                        val profile = when {
                            segment is DialogueSegment && segment.speakerId != null -> {
                                val character = bookCharacters.find { it.id == segment.speakerId }
                                when {
                                    character == null -> com.example.cititor.domain.model.VoiceProfiles.DEFAULT
                                    character.isProtagonist && character.gender == com.example.cititor.domain.model.Gender.MALE -> 
                                        com.example.cititor.domain.model.VoiceProfiles.HERO_MALE
                                    character.isProtagonist && character.gender == com.example.cititor.domain.model.Gender.FEMALE -> 
                                        com.example.cititor.domain.model.VoiceProfiles.HERO_FEMALE
                                    character.voiceProfile != null -> 
                                        com.example.cititor.domain.model.VoiceProfiles.getById(character.voiceProfile)
                                    else -> com.example.cititor.domain.model.VoiceProfiles.DEFAULT
                                }
                            }
                            segment is NarrationSegment -> com.example.cititor.domain.model.VoiceProfiles.NARRATOR
                            else -> com.example.cititor.domain.model.VoiceProfiles.DEFAULT
                        }

                        // 2. Apply Profile Base Parameters
                        speed *= profile.speed
                        pitch *= profile.pitch
                        
                        if (segment is DialogueSegment) {
                            val charName = bookCharacters.find { it.id == segment.speakerId }?.name ?: "Unknown"
                            Log.d(TAG, "Applying VoiceProfile '${profile.name}' for character '$charName'")
                        }

                        // 3. Apply Emotion Modulation if it's a Dialogue
                        if (segment is DialogueSegment) {
                            val (speedMult, pitchMult) = getEmotionMultipliers(segment.emotion, segment.intensity)
                            speed *= speedMult
                            pitch *= pitchMult
                        } else if (segment is NarrationSegment) {
                            if (segment.style == NarrationStyle.THOUGHT) {
                                speed *= 0.85f // Thoughts are usually slower
                                pitch *= 1.05f // And slightly higher pitch
                            }
                        }

                        // 3.5. Proper Name Emphasis
                        if (containsProperNames(text, properNames)) {
                            speed *= 0.92f
                        }
                        
                        // 4. Safety Clamping
                        val finalSpeed = speed.coerceIn(0.6f, 1.6f)
                        val finalPitch = pitch.coerceIn(0.5f, 1.5f)
                        
                        Log.d(TAG, "Synthesizing segment: '${text.take(30)}...' | Speed: $finalSpeed | Pitch: $finalPitch")
                        
                        var rawAudio: FloatArray? = null
                        if (segment is NarrationSegment && segment.style == NarrationStyle.CHAPTER_INDICATOR) {
                            Log.d(TAG, "Skipping synthesis for chapter indicator: $text")
                        } else {
                            rawAudio = piperTts?.synthesize(text, finalSpeed, speakerId)
                        }
                        
                        if (rawAudio != null) {
                            // Apply Pitch Shift if needed
                            if (finalPitch != 1.0f) {
                                val sampleRate = piperTts?.getSampleRate() ?: 22050
                                rawAudio = effectProcessor?.applyPitchShift(rawAudio, sampleRate, finalPitch.toDouble())
                            }
                            
                            if (rawAudio != null) {
                                currentChannel.send(rawAudio)
                                
                                // 5. Insert Intelligent Pause
                                val pauseDurationMs = getPauseDuration(text, nextSegment)
                                if (pauseDurationMs > 0) {
                                    val sampleRate = piperTts?.getSampleRate() ?: 22050
                                    val silence = generateSilence(pauseDurationMs, sampleRate)
                                    currentChannel.send(silence)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Producer error", e)
                } finally {
                    // Synthesis finished, but playback might still be ongoing.
                    // We close the local channel reference to signal the consumer.
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

    private var consecutiveCommas = 0

    private fun getPauseDuration(text: String, nextSegment: TextSegment?): Long {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0L

        val nextText = nextSegment?.text?.trim()?.lowercase() ?: ""
        
        // Rule 1: Paragraph breaks (already handled by \n\n in segments)
        if (text.contains("\n\n")) {
            consecutiveCommas = 0
            return 1000L
        }
        if (text.contains("\n")) {
            consecutiveCommas = 0
            return 800L
        }

        // Rule 2: Dialogue Tags (e.g. "—dijo él")
        // If current is dialogue and next is a tag, use a slightly longer pause (user requested 300ms)
        val isDialogue = text.startsWith("—") || text.startsWith("\"") || text.startsWith("«") || text.startsWith("'")
        val nextIsTag = nextText.startsWith("—") && nextText.length > 1 && nextText[1].isLowerCase()
        if (isDialogue && nextIsTag) {
            consecutiveCommas = 0
            return 300L
        }

        // Rule 3: Conjunctions (breath before "pero", "aunque", "y", "o", etc.)
        val nextIsConjunction = nextText.startsWith("pero") || 
                                nextText.startsWith("aunque") || 
                                nextText.startsWith("sin embargo") ||
                                nextText.startsWith("no obstante") ||
                                nextText.startsWith("y ") ||
                                nextText.startsWith("o ")
        
        // Base pause by punctuation
        var pause = when {
            trimmed.endsWith(".") || trimmed.endsWith("!") || trimmed.endsWith("?") -> {
                consecutiveCommas = 0
                800L
            }
            trimmed.endsWith(";") -> {
                consecutiveCommas = 0
                600L // User requested intermediate pause for semicolon
            }
            trimmed.endsWith(":") -> {
                consecutiveCommas = 0
                400L
            }
            trimmed.endsWith(",") -> {
                consecutiveCommas++
                // List Breath Logic: Add 50ms for each consecutive comma in a list
                val listExtra = if (consecutiveCommas > 1) (consecutiveCommas - 1) * 50L else 0L
                400L + listExtra
            }
            else -> {
                consecutiveCommas = 0
                250L // Default pause between intelligent segments
            }
        }

        // Add extra breath before conjunctions
        if (nextIsConjunction) {
            pause += 150L
        }

        return pause
    }

    private fun getEmotionMultipliers(emotion: com.example.cititor.domain.model.Emotion, intensity: Float): Pair<Float, Float> {
        val (baseSpeedMult, basePitchMult) = when (emotion) {
            com.example.cititor.domain.model.Emotion.NEUTRAL -> 1.0f to 1.0f
            com.example.cititor.domain.model.Emotion.JOY -> 1.1f to 1.1f
            com.example.cititor.domain.model.Emotion.SADNESS -> 0.8f to 0.9f
            com.example.cititor.domain.model.Emotion.ANGER -> 1.2f to 0.8f
            com.example.cititor.domain.model.Emotion.FEAR -> 1.3f to 1.2f
            com.example.cititor.domain.model.Emotion.SURPRISE -> 1.2f to 1.2f
            com.example.cititor.domain.model.Emotion.URGENCY -> 1.4f to 1.0f
            com.example.cititor.domain.model.Emotion.WHISPER -> 0.7f to 1.0f
            com.example.cititor.domain.model.Emotion.MYSTERY -> 0.85f to 0.85f
            com.example.cititor.domain.model.Emotion.SARCASM -> 1.0f to 1.15f
            com.example.cititor.domain.model.Emotion.PRIDE -> 0.95f to 0.85f
            com.example.cititor.domain.model.Emotion.DISGUST -> 0.9f to 0.9f
            com.example.cititor.domain.model.Emotion.EXHAUSTION -> 0.75f to 0.9f
            com.example.cititor.domain.model.Emotion.CONFUSION -> 0.9f to 1.1f
            com.example.cititor.domain.model.Emotion.TENDERNESS -> 0.85f to 1.05f
        }

        // Apply intensity: 0.0 means neutral, 1.0 means full effect
        val finalSpeedMult = 1.0f + (baseSpeedMult - 1.0f) * intensity
        val finalPitchMult = 1.0f + (basePitchMult - 1.0f) * intensity

        return finalSpeedMult to finalPitchMult
    }

    private fun generateSilence(durationMs: Long, sampleRate: Int): FloatArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        return FloatArray(numSamples) // All zeros = Silence
    }

    private fun containsProperNames(text: String, dictionary: Set<String>): Boolean {
        if (dictionary.isEmpty()) {
            // Fallback to heuristic if dictionary is not yet available
            val properNameRegex = Regex("""\s[A-ZÁÉÍÓÚ][a-záéíóúñ]+""")
            return properNameRegex.containsMatchIn(text)
        }
        
        // Check if any word in the dictionary is present in the text
        // We use a simple word boundary check
        return dictionary.any { name ->
            text.contains(name, ignoreCase = false)
        }
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
