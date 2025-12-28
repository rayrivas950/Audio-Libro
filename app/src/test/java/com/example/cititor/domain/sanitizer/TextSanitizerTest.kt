package com.example.cititor.domain.sanitizer

import com.example.cititor.domain.dictionary.DictionaryManager
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TextSanitizerTest {

    private lateinit var textSanitizer: TextSanitizer
    private val dictionaryManager = mockk<DictionaryManager>()

    @Before
    fun setup() {
        textSanitizer = TextSanitizer(dictionaryManager)
    }

    @Test
    fun `test fixPunctuation adds space after period followed by letter`() {
        val input = "Esto es una prueba.La palabra está pegada.Y otra más."
        val expected = "Esto es una prueba. La palabra está pegada. Y otra más."
        
        assertEquals(expected, textSanitizer.sanitize(input))
    }

    @Test
    fun `test fixPunctuation does not break decimal numbers`() {
        val input = "El valor es 3.14 o quizás 2.5."
        val expected = "El valor es 3.14 o quizás 2.5."
        
        assertEquals(expected, textSanitizer.sanitize(input))
    }

    @Test
    fun `test joinLines merges normal sentences and respects paragraphs`() {
        val input = """
            El antiguo castillo de Stormhold se alzaba
            majestuoso sobre la colina de obsidiana y sus
            torres desafiaban al cielo gris.
            
            Este es un nuevo párrafo.
        """.trimIndent()

        val expected = """
            El antiguo castillo de Stormhold se alzaba majestuoso sobre la colina de obsidiana y sus torres desafiaban al cielo gris.
            
            Este es un nuevo párrafo.
        """.trimIndent()

        assertEquals(expected, textSanitizer.sanitize(input))
    }

    @Test
    fun `test joinLines respects dialogue markers`() {
        val input = """
            Arturo asintió lentamente.
            —Tienes razón, viejo amigo.
            —Incluso cuando la lluvia cae.
        """.trimIndent()

        val expected = """
            Arturo asintió lentamente.
            —Tienes razón, viejo amigo.
            —Incluso cuando la lluvia cae.
        """.trimIndent()

        assertEquals(expected, textSanitizer.sanitize(input))
    }

    @Test
    fun `test joinLines respects geometric titles`() {
        val input = """
            [GEOMETRIC_TITLE] El brujo
            [GEOMETRIC_TITLE] I
            Después dijeron que aquel hombre había venido
            desde el norte por la Puerta de los Cordeleros.
        """.trimIndent()

        val expected = """
            El brujo
            I
            Después dijeron que aquel hombre había venido desde el norte por la Puerta de los Cordeleros.
        """.trimIndent()

        assertEquals(expected, textSanitizer.sanitize(input))
    }

    @Test
    fun `test joinLines respects numbered lists`() {
        val input = """
            Pasos a seguir:
            1. Lavar las manos.
            2. Abrir la puerta.
            3. Entrar.
        """.trimIndent()

        val expected = """
            Pasos a seguir:
            1. Lavar las manos.
            2. Abrir la puerta.
            3. Entrar.
        """.trimIndent()

        assertEquals(expected, textSanitizer.sanitize(input))
    }

    @Test
    fun `test combined logic works as expected`() {
        val input = """
            [GEOMETRIC_TITLE] Capitulo X
            Gritó Sir Lancelot.
            ¡Por el honor
            de la Mesa Redonda!
            Todo terminó.Repentinamente.
        """.trimIndent()

        val expected = """
            Capitulo X
            Gritó Sir Lancelot.
            ¡Por el honor de la Mesa Redonda!
            Todo terminó. Repentinamente.
        """.trimIndent()

        assertEquals(expected, textSanitizer.sanitize(input))
    }
}
