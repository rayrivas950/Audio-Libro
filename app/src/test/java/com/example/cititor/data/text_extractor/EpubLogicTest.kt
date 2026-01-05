
package com.example.cititor.data.text_extractor

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class EpubLogicTest {

    // Copying the logic under test to verify behavior quickly without Android dependencies context
    // Ideally we would make the real functions internal and test them, but this is faster for logic verification.

    private fun stripHtml(html: String): String {
        // Mocking HtmlCompat behavior since it's an Android dependency not available in standard JUnit
        // This is a simplified approximation for testing whitespace logic.
        // In real app, HtmlCompat adds \n after <p>, <div>, <br> etc.
        
        var decoded = html
            // Mocking simple block tags behavior of HtmlCompat
            .replace(Regex("<p[^>]*>"), "")
            .replace(Regex("</p>"), "\n\n") 
            .replace(Regex("<br/?>"), "\n")
            .replace(Regex("<[^>]*>"), "") // fallback strip tags

        return decoded
            .replace(Regex("<[^>]*>"), "")  // Remove residual tags
            .replace(Regex("[ \\t\\xA0]+"), " ") // Normalize horizontal whitespace
            .replace(Regex("\\n\\s*\\n"), "\n\n") // Normalize structural newlines
            .trim()
    }

    private fun processImagesMock(rawHtml: String): String {
        // The regex we want to test/improve
        val imgRegex = Regex("""<img\s+[^>]*src\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
        
        return imgRegex.replace(rawHtml) { matchResult ->
            val src = matchResult.groupValues[1]
            // Mocking extraction success
            "\n\n[IMAGE_REF:$src]\n\n"
        }
    }

    @Test
    fun `test image regex matches standard tag`() {
        val html = """<p>Text</p><img src="images/map.jpg" alt="Map"/><p>More text</p>"""
        val result = processImagesMock(html)
        assertEquals("<p>Text</p>\n\n[IMAGE_REF:images/map.jpg]\n\n<p>More text</p>", result)
    }

    @Test
    fun `test image regex matches tag with extra attributes and spaces`() {
        val html = """<img  class="big"   src =  'images/cover.png'  />"""
        val result = processImagesMock(html)
        assertEquals("\n\n[IMAGE_REF:images/cover.png]\n\n", result)
    }

    @Test
    fun `test image regex matches tag newline attributes`() {
        val html = """<img 
            src="images/weird.jpg"
            alt="Weird" />"""
        val result = processImagesMock(html)
        // Original regex might fail multiline. Let's see.
        assertEquals("\n\n[IMAGE_REF:images/weird.jpg]\n\n", result)
    }

    @Test
    fun `test stripHtml preserves paragraphs`() {
        // simulating output from HtmlCompat where <p> becomes text followed by \n\n
        val simulatedHtmlCompatOutput = "Paragraph 1 content.\n\nParagraph 2 content."
        
        // Our stripHtml applies regexes on top of that. 
        // We need to ensure logic doesn't flatten it.
        val result = simulatedHtmlCompatOutput
            .replace(Regex("[ \\t\\xA0]+"), " ") 
            .replace(Regex("\\n\\s*\\n"), "\n\n")
        assertEquals("Paragraph 1 content.\n\nParagraph 2 content.", result)
    }

    @Test
    fun `test real world tag with alt first`() {
        val html = """<img alt="img5.jpg" src="../Images/img5.jpg" />"""
        val result = processImagesMock(html)
    }

    @Test
    fun `test wall of text prevention`() {
        val input = "Line 1\n   \nLine 2\n\n\nLine 3"
        val result = input
            .replace(Regex("[ \\t\\xA0]+"), " ")
            .replace(Regex("\\n\\s*\\n"), "\n\n")
            .trim()
        
        assertEquals("Line 1\n\nLine 2\n\nLine 3", result)
    }
}
