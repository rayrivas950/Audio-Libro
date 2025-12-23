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
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _currentSpokenWord = MutableStateFlow<IntRange?>(null)
    val currentSpokenWord: StateFlow<IntRange?> = _currentSpokenWord

    companion object {
        private const val TAG = "TTSManager"
        private const val NORMAL_PITCH = 1.0f
        private const val DIALOGUE_PITCH = 1.1f
    }

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.d(TAG, "TTS Initialization successful.")
            isInitialized = true
            tts?.language = Locale.getDefault()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {}
                override fun onError(utteranceId: String?) {}
                
                // WARNING: This will be broken until we add offset logic.
                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                     _currentSpokenWord.value = IntRange(start, end)
                }
            })
        } else {
            Log.e(TAG, "TTS Initialization failed with status: $status")
        }
    }

    fun speak(segments: List<TextSegment>) {
        if (!isInitialized) return

        _currentSpokenWord.value = null
        // Clear any previous items in the queue before starting.
        tts?.speak("", TextToSpeech.QUEUE_FLUSH, null, null)

        segments.forEach {
            val utteranceId = UUID.randomUUID().toString()
            when (it) {
                is NarrationSegment -> {
                    tts?.setPitch(NORMAL_PITCH)
                    tts?.speak(it.text, TextToSpeech.QUEUE_ADD, null, utteranceId)
                }
                is DialogueSegment -> {
                    tts?.setPitch(DIALOGUE_PITCH)
                    tts?.speak(it.text, TextToSpeech.QUEUE_ADD, null, utteranceId)
                }
            }
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
