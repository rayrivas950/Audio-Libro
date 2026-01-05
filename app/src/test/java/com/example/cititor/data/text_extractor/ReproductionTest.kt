
package com.example.cititor.data.text_extractor

import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class ReproductionTest {

    @Test
    fun `reproduce image finding logic`() {
        val epubPath = "/home/raynor/Escritorio/Proyectos/audio-libro/El Senor de los Anillos - J. R. R. Tolkien.epub"
        val epubFile = File(epubPath)
        
        if (!epubFile.exists()) {
            println("EPUB file not found at $epubPath")
            return
        }
        
        println("Opening EPUB: ${epubFile.absolutePath}")
        
        // The src we found in the HTML
        val srcFromHtml = "../Images/img5.jpg"
        
        // The logic from EpubExtractor
        val cleanName = srcFromHtml.substringAfterLast("/")
        println("Looking for image: src='$srcFromHtml', cleanName='$cleanName'")

        ZipFile(epubFile).use { zip ->
            var entry = zip.getEntry(srcFromHtml)
            
            if (entry != null) {
                println("FOUND by direct path: ${entry.name}")
            } else {
                println("NOT FOUND by direct path")
                
                val allEntries = zip.entries().asSequence().toList()
                println("Total entries in ZIP: ${allEntries.size}")
                
                // Debug: Print some entries to see what they look like
                allEntries.take(10).forEach { println(" - ${it.name}") }
                
                entry = allEntries.find { 
                    it.name.endsWith(srcFromHtml) || it.name.endsWith("/$cleanName") || it.name == cleanName 
                    // Note: I tweaked the logic slightly here to be safer (endsWith /name)
                    // The original code was: it.name.endsWith(imagePath) || it.name.endsWith(cleanName)
                }
                

                // Testing EXACT original logic
                val originalMatch = allEntries.find { it.name.endsWith(srcFromHtml) || it.name.endsWith(cleanName) }
                
                if (originalMatch != null) {
                    println("FOUND by fuzzy match (Original Logic): ${originalMatch.name}")
                }
            }
            
            // NEW TEST: Read 1-001.xhtml and test Regex
            val xhtmlEntry = zip.getEntry("OEBPS/Text/1-001.xhtml")
            if (xhtmlEntry != null) {
                println("\n--- Testing Regex on 1-001.xhtml ---")
                val content = zip.getInputStream(xhtmlEntry).bufferedReader().use { it.readText() }
                
                // The regex we use in app
                val imgRegex = Regex("""<img\s+[^>]*src\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
                
                val matches = imgRegex.findAll(content).toList()
                println("Found ${matches.size} matches in 1-001.xhtml")
                matches.forEach { 
                    println("MATCH: ${it.value}")
                    println("   SRC capture: ${it.groupValues[1]}")
                }
                
                if (matches.isEmpty()) {
                    println("FAIL: Regex found ZERO matches!")
                    // Print a snippet where we expect the image to be
                    val idx = content.indexOf("img")
                    if (idx != -1) {
                         println("Context around first 'img':")
                         println(content.substring(maxOf(0, idx - 50), minOf(content.length, idx + 100)))
                    }
                }
            } else {
                println("Could not find OEBPS/Text/1-001.xhtml to test regex")
            }
        }
    }
}
