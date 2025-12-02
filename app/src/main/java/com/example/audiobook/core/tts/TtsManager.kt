package com.example.audiobook.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()

    private lateinit var tts: TextToSpeech

    fun initialize() {
        if (!::_isInitialized.isInitialized || !_isInitialized.value) {
            tts = TextToSpeech(context, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale("es", "ES"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Manejar error de lenguaje no soportado
            }
            _isInitialized.value = true
        } else {
            // Manejar error de inicialización
        }
    }

    fun speak(text: String, utteranceId: String) {
        if (_isInitialized.value) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    fun shutdown() {
        if (::_isInitialized.isInitialized && _isInitialized.value) {
            tts.stop()
            tts.shutdown()
        }
    }

    fun setOnUtteranceProgressListener(listener: UtteranceProgressListener) {
        if (::_isInitialized.isInitialized && _isInitialized.value) {
            tts.setOnUtteranceProgressListener(listener)
        }
    }
}
