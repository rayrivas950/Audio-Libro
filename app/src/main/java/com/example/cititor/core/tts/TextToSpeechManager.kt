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

    fun speak(segments: List<TextSegment>, bookCharacters: List<com.example.cititor.domain.model.Character> = emptyList(), properNames: Set<String> = emptySet()) {
        if (!isInitialized) return

        stop() // Stop any ongoing playback
        _currentSpokenWord.value = null
        
        if (usePiper) {
            // 1. Consumer: Plays audio from the channel
            playbackJob = scope.launch {
                for (audioData in audioChannel) {
                    audioPlayer?.play(audioData)
                }
                Log.d(TAG, "Playback consumer finished.")
            }

            // 2. Producer: Synthesizes segments and sends to channel
            synthesisJob = scope.launch {
                try {
                    var lastSpeakerId: String? = null
                    
                    segments.forEach { segment ->
                        val text = segment.text
                        
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
                            // TODO: Map speakerId to Piper speaker index
                        } else if (segment is NarrationSegment) {
                            if (segment.style == NarrationStyle.THOUGHT) {
                                speed *= 0.85f // Thoughts are usually slower
                                pitch *= 1.05f // And slightly higher pitch
                            }
                        }

                        // 3.5. Proper Name Emphasis (Places or Characters)
                        // If segment contains capitalized words in the middle, slow down slightly for clarity
                        if (containsProperNames(text, properNames)) {
                            speed *= 0.92f // 8% slower for segments with important names
                            Log.d(TAG, "Proper name detected in: '$text'. Slowing down for emphasis.")
                        }
                        
                        // 4. Safety Clamping
                        val finalSpeed = speed.coerceIn(0.6f, 1.6f)
                        val finalPitch = pitch.coerceIn(0.5f, 1.5f)
                        
                        Log.d(TAG, "Synthesizing segment: '${text.take(30)}...' | Emotion: ${if (segment is DialogueSegment) segment.emotion else "NARRATION"} | Speed: $finalSpeed | Pitch: $finalPitch")
                        
                        var rawAudio = piperTts?.synthesize(text, finalSpeed, speakerId)
                        
                        if (rawAudio != null) {
                            // Apply Pitch Shift if needed
                            if (finalPitch != 1.0f) {
                                val sampleRate = piperTts?.getSampleRate() ?: 22050
                                rawAudio = effectProcessor?.applyPitchShift(rawAudio, sampleRate, finalPitch.toDouble())
                            }
                            
                            if (rawAudio != null) {
                                Log.d(TAG, "Synthesis successful. Sending to pipeline. Size: ${rawAudio.size}")
                                audioChannel.send(rawAudio)
                                
                                // 5. Insert Natural Pause based on punctuation (Literary Standards)
                                val pauseDurationMs = when {
                                    text.endsWith(",") || text.endsWith(";") || text.endsWith(":") -> 400L
                                    text.endsWith(".") || text.endsWith("!") || text.endsWith("?") -> 800L
                                    text.length > 80 -> 350L // Breath after long sentences (Threshold reduced to 80)
                                    else -> 200L 
                                }
                                
                                val sampleRate = piperTts?.getSampleRate() ?: 22050
                                val silence = generateSilence(pauseDurationMs, sampleRate)
                                audioChannel.send(silence)
                            }
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

    fun setMasterSpeed(speed: Float) {
        this.masterSpeed = speed.coerceIn(0.1f, 2.0f)
        Log.d(TAG, "Master speed updated to: $masterSpeed")
    }

    private fun getEmotionMultipliers(emotion: Emotion, intensity: Float): Pair<Float, Float> {
        val (baseSpeedMult, basePitchMult) = when (emotion) {
            Emotion.NEUTRAL -> 1.0f to 1.0f
            Emotion.JOY -> 1.1f to 1.1f
            Emotion.SADNESS -> 0.8f to 0.9f
            Emotion.ANGER -> 1.2f to 0.8f
            Emotion.FEAR -> 1.3f to 1.2f
            Emotion.SURPRISE -> 1.2f to 1.2f
            Emotion.URGENCY -> 1.4f to 1.0f
            Emotion.WHISPER -> 0.7f to 1.0f
            Emotion.MYSTERY -> 0.85f to 0.85f
            Emotion.SARCASM -> 1.0f to 1.15f
            Emotion.PRIDE -> 0.95f to 0.85f
            Emotion.DISGUST -> 0.9f to 0.9f
            Emotion.EXHAUSTION -> 0.75f to 0.9f
            Emotion.CONFUSION -> 0.9f to 1.1f
            Emotion.TENDERNESS -> 0.85f to 1.05f
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
