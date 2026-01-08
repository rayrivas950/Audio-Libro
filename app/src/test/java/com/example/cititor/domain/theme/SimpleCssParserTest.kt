package com.example.cititor.domain.theme

import org.junit.Assert.*
import org.junit.Test

class SimpleCssParserTest {
    
    private val parser = SimpleCssParser()
    
    @Test
    fun testParseFontFace() {
        val css = """
            @font-face{font-family:"Ringbearer";font-style:normal;font-weight:normal;src:url(../Fonts/RINGM_.TTF)}
            @font-face{font-family:"Tolkien";font-style:normal;font-weight:normal;src:url(../Fonts/tolkien.ttf)}
        """.trimIndent()
        
        val theme = parser.parse(css)
        
        assertEquals(2, theme.fonts.size)
        assertEquals("../Fonts/RINGM_.TTF", theme.fonts["Ringbearer"])
        assertEquals("../Fonts/tolkien.ttf", theme.fonts["Tolkien"])
    }
    
    @Test
    fun testParseTagStyles() {
        val css = """
            h1 { 
                font-family:Ringbearer,Times;
                font-size:1.8em; 
                text-align:center; 
            }
            
            body { font-family: "Times New Roman", Times, serif; margin:1em; }
        """.trimIndent()
        
        val theme = parser.parse(css)
        
        // Verificar h1
        val h1Style = theme.getStyleForTag("h1")
        assertNotNull(h1Style)
        assertEquals("Ringbearer,Times", h1Style?.fontFamily)
        assertEquals(1.8f, h1Style?.fontSizeEm)
        assertEquals("center", h1Style?.textAlign)
        
        // Verificar body
        val bodyStyle = theme.getStyleForTag("body")
        assertNotNull(bodyStyle)
        assertTrue(bodyStyle?.fontFamily?.contains("Times New Roman") == true)
    }
    
    @Test
    fun testParseClassStyles() {
        val css = """
            .capital  {
                font-family:Tolkien,sans-serif;
                font-size: 300%; 
                font-weight: bold; 
            }
            
            .poema {
                font-style: italic; 
                text-align:left; 
            }
        """.trimIndent()
        
        val theme = parser.parse(css)
        
        // Verificar .capital
        val capitalStyle = theme.getStyleForClass("capital")
        assertNotNull(capitalStyle)
        assertEquals("Tolkien,sans-serif", capitalStyle?.fontFamily)
        assertEquals(3.0f, capitalStyle?.fontSizeEm) // 300% = 3.0em
        assertEquals("bold", capitalStyle?.fontWeight)
        
        // Verificar .poema
        val poemaStyle = theme.getStyleForClass("poema")
        assertNotNull(poemaStyle)
        assertEquals("italic", poemaStyle?.fontStyle)
        assertEquals("left", poemaStyle?.textAlign)
    }
    
    @Test
    fun testParseTolkienStyleCss() {
        // CSS real del libro de Tolkien
        val css = """
            @font-face{font-family:"Ringbearer";font-style:normal;font-weight:normal;src:url(../Fonts/RINGM_.TTF)}
            @font-face{font-family:"Tolkien";font-style:normal;font-weight:normal;src:url(../Fonts/tolkien.ttf)}
            
            body { font-family: "Times New Roman", Times, serif; margin:1em; }
            
            h1 { 
                font-family:Ringbearer,Times;
                font-size:1.8em; 
                padding-top:0;  
                display:block; 
                text-align:center; 
                margin-top:35%;
            }
            
            h3 { 
                font-family:Tolkien,Times;
                font-size:1.4em; 
                padding-top:0;  
                display:block; 
                text-align:center; 
                margin-top:35%;
            }
            
            .capital  {
                font-family:Tolkien,sans-serif;
                font-size: 300%; 
                font-weight: bold; 
                float: left; 
            }
        """.trimIndent()
        
        val theme = parser.parse(css)
        
        // Verificar fuentes
        assertEquals(2, theme.fonts.size)
        assertNotNull(theme.getFontPath("Ringbearer"))
        assertNotNull(theme.getFontPath("Tolkien"))
        
        // Verificar estilos de etiquetas
        assertEquals("Ringbearer,Times", theme.getStyleForTag("h1")?.fontFamily)
        assertEquals("Tolkien,Times", theme.getStyleForTag("h3")?.fontFamily)
        
        // Verificar estilos de clases
        assertEquals(3.0f, theme.getStyleForClass("capital")?.fontSizeEm)
    }
    
    @Test
    fun testGetPrimaryFontFamily() {
        val style = CssTextStyle(fontFamily = "Ringbearer,Times,serif")
        assertEquals("Ringbearer", style.getPrimaryFontFamily())
        
        val styleWithQuotes = CssTextStyle(fontFamily = "\"Times New Roman\", Times, serif")
        assertEquals("Times New Roman", styleWithQuotes.getPrimaryFontFamily())
    }
    
    @Test
    fun testMergeStyles() {
        val base = CssTextStyle(
            fontFamily = "Times",
            fontSizeEm = 1.0f,
            fontWeight = "normal"
        )
        
        val override = CssTextStyle(
            fontSizeEm = 1.5f,
            fontStyle = "italic"
        )
        
        val merged = override.mergeWith(base)
        
        assertEquals("Times", merged.fontFamily) // De base
        assertEquals(1.5f, merged.fontSizeEm) // De override
        assertEquals("normal", merged.fontWeight) // De base
        assertEquals("italic", merged.fontStyle) // De override
    }
}
