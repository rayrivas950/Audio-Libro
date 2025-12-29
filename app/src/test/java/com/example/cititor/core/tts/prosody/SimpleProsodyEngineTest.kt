package com.example.cititor.core.tts.prosody

import com.example.cititor.domain.model.BookCategory
import com.example.cititor.domain.model.NarrationSegment
import com.example.cititor.domain.model.NarrationStyle
import com.example.cititor.domain.model.ProsodyIntention
import org.junit.Assert.assertEquals
import org.junit.Test

class SimpleProsodyEngineTest {

    private val engine = SimpleProsodyEngine()

    @Test
    fun `calculateParameters applies emphasis to CHAPTER_INDICATOR`() {
        val chapterSegment = NarrationSegment(
            text = "Chapter 1",
            intention = ProsodyIntention.NEUTRAL,
            style = NarrationStyle.CHAPTER_INDICATOR
        )

        val params = engine.calculateParameters(chapterSegment, 1.0f, BookCategory.FICTION)

        assertEquals(1500L, params.pausePost)
        assertEquals(0.9f, params.speed!!, 0.01f) // Should be slowed down
        assertEquals(1.1f, params.volume!!, 0.01f) // Should be louder
    }

    @Test
    fun `calculateParameters keeps neutral defaults for normal narration`() {
        val normalSegment = NarrationSegment(
            text = "This is a normal sentence.",
            intention = ProsodyIntention.NEUTRAL,
            style = NarrationStyle.NEUTRAL
        )

        val params = engine.calculateParameters(normalSegment, 1.0f, BookCategory.FICTION)

        assertEquals(null, params.pausePost)
        assertEquals(1.0f, params.speed!!, 0.01f)
        assertEquals(1.0f, params.volume!!, 0.01f)
    }
}
