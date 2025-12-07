package com.example.audiobook.core.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.util.Locale

@ExperimentalCoroutinesApi
class TtsManagerTest {

    private lateinit var mockContext: Context
    private lateinit var mockTextToSpeech: TextToSpeech
    private lateinit var ttsManager: TtsManager

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        // Mock TextToSpeech constructor - this is a bit tricky
        // We'll capture the OnInitListener and call it manually
        mockkConstructor(TextToSpeech::class)
        every { anyConstructed<TextToSpeech>().setLanguage(any()) } returns TextToSpeech.LANG_AVAILABLE
        every { anyConstructed<TextToSpeech>().stop() } just Runs
        every { anyConstructed<TextToSpeech>().shutdown() } just Runs
        every { anyConstructed<TextToSpeech>().speak(any(), any(), any(), any()) } returns TextToSpeech.SUCCESS

        ttsManager = TtsManager(mockContext)
    }

    @After
    fun teardown() {
        // Clear all mocks and allow TextToSpeech constructor to be unmocked
        unmockkAll()
    }

    @Test
    fun `initialize sets isInitialized to true on success`() = runTest {
        val capturedListener = slot<TextToSpeech.OnInitListener>()
        mockkConstructor(TextToSpeech::class)
        every {
            TextToSpeech(mockContext, capture(capturedListener))
        } answers {
            mockTextToSpeech = mockk(relaxed = true)
            capturedListener.captured.onInit(TextToSpeech.SUCCESS)
            mockTextToSpeech
        }

        ttsManager.initialize()

        assertTrue(ttsManager.isInitialized.first())
        verify { mockTextToSpeech.setLanguage(Locale("es", "ES")) }
    }

    @Test
    fun `initialize handles language not supported`() = runTest {
        val capturedListener = slot<TextToSpeech.OnInitListener>()
        mockkConstructor(TextToSpeech::class)
        every {
            TextToSpeech(mockContext, capture(capturedListener))
        } answers {
            mockTextToSpeech = mockk(relaxed = true)
            every { mockTextToSpeech.setLanguage(any()) } returns TextToSpeech.LANG_NOT_SUPPORTED
            capturedListener.captured.onInit(TextToSpeech.SUCCESS)
            mockTextToSpeech
        }

        ttsManager.initialize()

        assertFalse(ttsManager.isInitialized.first()) // Should remain false or handle error
        // A real scenario might update an error state or log, but for now, it should not be initialized
    }

    @Test
    fun `initialize handles TextToSpeech error`() = runTest {
        val capturedListener = slot<TextToSpeech.OnInitListener>()
        mockkConstructor(TextToSpeech::class)
        every {
            TextToSpeech(mockContext, capture(capturedListener))
        } answers {
            mockTextToSpeech = mockk(relaxed = true)
            capturedListener.captured.onInit(TextToSpeech.ERROR)
            mockTextToSpeech
        }

        ttsManager.initialize()

        assertFalse(ttsManager.isInitialized.first())
    }

    @Test
    fun `speak calls tts speak when initialized`() = runTest {
        // Mock TextToSpeech constructor with success
        val capturedListener = slot<TextToSpeech.OnInitListener>()
        mockkConstructor(TextToSpeech::class)
        every {
            TextToSpeech(mockContext, capture(capturedListener))
        } answers {
            mockTextToSpeech = mockk(relaxed = true)
            every { mockTextToSpeech.setLanguage(any()) } returns TextToSpeech.LANG_AVAILABLE
            capturedListener.captured.onInit(TextToSpeech.SUCCESS)
            mockTextToSpeech
        }

        ttsManager.initialize()
        val testText = "Hello world"
        val utteranceId = "testId"
        ttsManager.speak(testText, utteranceId)

        verify { mockTextToSpeech.speak(testText, TextToSpeech.QUEUE_FLUSH, null, utteranceId) }
    }

    @Test
    fun `speak does not call tts speak when not initialized`() = runTest {
        // Don't initialize ttsManager
        val testText = "Hello world"
        val utteranceId = "testId"
        ttsManager.speak(testText, utteranceId)

        verify(exactly = 0) { anyConstructed<TextToSpeech>().speak(any(), any(), any(), any()) }
    }

    @Test
    fun `shutdown stops and shuts down tts`() = runTest {
        // Initialize ttsManager first
        val capturedListener = slot<TextToSpeech.OnInitListener>()
        mockkConstructor(TextToSpeech::class)
        every {
            TextToSpeech(mockContext, capture(capturedListener))
        } answers {
            mockTextToSpeech = mockk(relaxed = true)
            every { mockTextToSpeech.setLanguage(any()) } returns TextToSpeech.LANG_AVAILABLE
            capturedListener.captured.onInit(TextToSpeech.SUCCESS)
            mockTextToSpeech
        }

        ttsManager.initialize()
        ttsManager.shutdown()

        verify { mockTextToSpeech.stop() }
        verify { mockTextToSpeech.shutdown() }
    }
    
    @Test
    fun `setOnUtteranceProgressListener sets listener when initialized`() = runTest {
        // Initialize ttsManager first
        val capturedListener = slot<TextToSpeech.OnInitListener>()
        mockkConstructor(TextToSpeech::class)
        every {
            TextToSpeech(mockContext, capture(capturedListener))
        } answers {
            mockTextToSpeech = mockk(relaxed = true)
            every { mockTextToSpeech.setLanguage(any()) } returns TextToSpeech.LANG_AVAILABLE
            capturedListener.captured.onInit(TextToSpeech.SUCCESS)
            mockTextToSpeech
        }
        
        val mockUtteranceListener = mockk<UtteranceProgressListener>()
        
        ttsManager.initialize()
        ttsManager.setOnUtteranceProgressListener(mockUtteranceListener)
        
        verify { mockTextToSpeech.setOnUtteranceProgressListener(mockUtteranceListener) }
    }

    @Test
    fun `setOnUtteranceProgressListener does not set listener when not initialized`() = runTest {
        val mockUtteranceListener = mockk<UtteranceProgressListener>()
        ttsManager.setOnUtteranceProgressListener(mockUtteranceListener)
        
        verify(exactly = 0) { anyConstructed<TextToSpeech>().setOnUtteranceProgressListener(any()) }
    }
}
