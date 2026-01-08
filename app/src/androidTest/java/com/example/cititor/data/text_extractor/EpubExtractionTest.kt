package com.example.cititor.data.text_extractor

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubExtractionTest {

    private val extractor = EpubExtractor()

    @Test
    fun testTitleH1Injection() {
        val html = """
            <html>
            <body>
                <h1>Libro Primero</h1>
                <p>Normal text</p>
            </body>
            </html>
        """.trimIndent()
        
        val result = extractor.stripHtml(html)
        
        // Should contain TITLE_L markers
        assertTrue("Result should contain TITLE_L markers", result.contains("[TITLE_L]Libro Primero[/TITLE_L]"))
    }

    @Test
    fun testTitleH2H3Injection() {
        val html = """
            <body>
                <h2>Capitulo I</h2>
                <h3>Subtitulo</h3>
            </body>
        """.trimIndent()
        
        val result = extractor.stripHtml(html)
        
        assertTrue("Result should contain TITLE_M for H2", result.contains("[TITLE_M]Capitulo I[/TITLE_M]"))
        assertTrue("Result should contain TITLE_M for H3", result.contains("[TITLE_M]Subtitulo[/TITLE_M]"))
    }

    @Test
    fun testStyledSplitInsideHeader() {
        val html = """
            <h1>9 Bajo la enseña del <span class="normal">Poney Pisador</span></h1>
        """.trimIndent()
        
        val result = extractor.stripHtml(html)
        
        // The span should have been replaced by newlines
        assertTrue("Should contain split in title", result.contains("9 Bajo la enseña del"))
        assertTrue("Should contain second part in title block", result.contains("Poney Pisador"))
        
        // Verify we didn't lose the markers
        assertTrue("Marker L should start", result.contains("[TITLE_L]"))
        assertTrue("Marker L should end", result.contains("[/TITLE_L]"))
    }

    @Test
    fun testFontSizeDetection() {
        val html = """
            <p style="font-size: 2em;">Title with font size</p>
            <div style="font-size: xx-large;">Large title</div>
        """.trimIndent()
        
        val result = extractor.stripHtml(html)
        
        assertTrue("Should detect TITLE_L from 2em", result.contains("[TITLE_L]Title with font size[/TITLE_L]"))
        assertTrue("Should detect TITLE_L from xx-large", result.contains("[TITLE_L]Large title[/TITLE_L]"))
    }

    @Test
    fun testClassBasedTitleDetection() {
        val html = """
            <p class="title">Title by Class</p>
            <div class="chapter-name">Chapter Name</div>
        """.trimIndent()
        
        val result = extractor.stripHtml(html)
        
        assertTrue("Should detect TITLE_L via class='title'", result.contains("[TITLE_L]Title by Class[/TITLE_L]"))
        assertTrue("Should detect TITLE_M via class='chapter'", result.contains("[TITLE_M]Chapter Name[/TITLE_M]"))
    }

    @Test
    fun testTolkienChapter3Structure() {
        val html = """
            <body>
              <h3 id="heading_id_2">3<br />
              Tres es compañía</h3>
              <p class="asangre"><span class="capital">T</span>ienes que irte en silencio, y pronto —dijo Gandalf.</p>
            </body>
        """.trimIndent()
        
        val result = extractor.stripHtml(html)
        
        // Should have "3" and "Tres es compañía" as titles, and the paragraph separately
        assertTrue("Should contain 3 as title", result.contains("[TITLE_M]3[/TITLE_M]"))
        assertTrue("Should contain name as title", result.contains("[TITLE_M]Tres es compañía[/TITLE_M]"))
        assertTrue("Should contain paragraph", result.contains("Tienes que irte en silencio"))
        assertTrue("Paragraph should NOT be titled", !result.contains("[TITLE_M]Tienes que irte"))
    }

    @Test
    fun testBlockquoteDramatic() {
        val html = """
            <p>Grito:</p>
            <blockquote>
                <p class="centrado">¡DESPERTAD! ¡FUEGO!</p>
                <p class="centrado">¡ENEMIGOS!</p>
            </blockquote>
        """.trimIndent()
        
        val result = extractor.stripHtml(html)
        
        assertTrue("Should contain QUOTE marker", result.contains("[QUOTE]¡DESPERTAD! ¡FUEGO![/QUOTE]"))
        assertTrue("Should contain second QUOTE marker", result.contains("[QUOTE]¡ENEMIGOS![/QUOTE]"))
    }

    @Test
    fun testFootnoteCleaning() {
        val html = """
            <p>Valandil 249,<a id="a8"></a><a href="../Text/notes.html#a77"><span class="super">[9]</span></a> Eldacar 339</p>
        """.trimIndent()
        
        val result = extractor.stripHtml(html)
        
        assertTrue("Should contain text", result.contains("Valandil 249, Eldacar 339"))
        assertFalse("Should NOT contain footnote number", result.contains("[9]"))
    }

    @Test
    fun testRingVersePoetry() {
        val html = """
            <blockquote>
                <p class="poema">Tres Anillos para los Reyes Elfos</p>
                <p class="poema">Siete para los Señores Enanos</p>
            </blockquote>
        """.trimIndent()
        
        val result = extractor.stripHtml(html)
        
        // Should be POEM, not QUOTE
        assertTrue("Should contain POEM marker", result.contains("[POEM]Tres Anillos para los Reyes Elfos[/POEM]"))
        assertFalse("Should NOT contain QUOTE marker", result.contains("[QUOTE]"))
    }

    @Test
    fun testDefinitiveBlockSeparation() {
        // This test ensures that the blocks are separated by the Definitive Token (|||BLOCK|||)
        // bypassing any HtmlCompat collapsing issues.
        val html = """
            <h1>Title</h1>
            <p>Paragraph 1.</p>
            <p>Paragraph 2.</p>
        """.trimIndent()
        
        val result = extractor.stripHtml(html)
        
        // We expect: [TITLE_L]Title[/TITLE_L]|||BLOCK|||Paragraph 1.|||BLOCK|||Paragraph 2.
        assertTrue("Blocks must be separated by BLOCK_SEPARATOR", result.contains("|||BLOCK|||"))
    }
}
