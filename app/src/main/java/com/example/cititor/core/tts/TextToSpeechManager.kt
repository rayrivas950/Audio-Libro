package com.example.cititor.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var queuedText: String? = null

    private val _currentSpokenWord = MutableStateFlow<IntRange?>(null)
    val currentSpokenWord: StateFlow<IntRange?> = _currentSpokenWord

    companion object {
        private const val TAG = "TTSManager"
    }

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.d(TAG, "TTS Initialization successful.")
            isInitialized = true
            tts?.setLanguage(Locale.getDefault())
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "Utterance started: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "Utterance done: $utteranceId")
                }

                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "Utterance error: $utteranceId")
                }

                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    Log.d(TAG, "onRangeStart: word '...${queuedText?.substring(start, end)}...' at range [$start, $end]")
                    _currentSpokenWord.value = IntRange(start, end)
                }
            })

            // Speak any queued text
            queuedText?.let {
                speak(it)
                queuedText = null
            }
        } else {
            Log.e(TAG, "TTS Initialization failed with status: $status")
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            Log.d(TAG, "Speaking: ${text.substring(0, 20)}...")
            _currentSpokenWord.value = null
            queuedText = text // Keep a reference for logging in onRangeStart
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId_1")
        } else {
            Log.d(TAG, "TTS not initialized, queuing text.")
            queuedText = text
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
