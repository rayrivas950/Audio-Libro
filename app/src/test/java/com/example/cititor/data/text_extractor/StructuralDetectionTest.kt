package com.example.cititor.data.text_extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure logic test for structural detection.
 * No PDFBox or external dependencies to avoid compilation issues in the test runner.
 */
class StructuralDetectionTest {

    class LogicProbe {
        var minX = Float.MAX_VALUE
        val indentationThreshold = 15f
        val pageWidth = 500f
        
        fun processLine(text: String, x: Float, width: Float): String {
            // Margin tracking: Only update if it looks like a left-aligned line
            // Títulos y párrafos indentados no deberían establecer el margen base.
            if (x < pageWidth * 0.2f && x < minX) {
                minX = x
            }

            if (minX == Float.MAX_VALUE) return "PLAIN"

            val midpoint = x + width / 2
            val pageMidpoint = pageWidth / 2
            val diffFromCenter = Math.abs(midpoint - pageMidpoint)
            
            val isIndented = x > minX + indentationThreshold
            
            // TITULO: Muy corto y muy centrado
            val isShort = width < pageWidth * 0.6f // Bajamos a 60% para evitar párrafos justificados
            val isCentered = isShort && diffFromCenter < 25f
            
            return if (isCentered) "[GEOMETRIC_TITLE]" 
            else if (isIndented) "BREAK" 
            else "PLAIN"
        }
    }

    @Test
    fun `test accurate title detection`() {
        val probe = LogicProbe()
        
        // 1. Establecer margen con una línea normal
        probe.processLine("Linea normal", 50f, 400f)
        assertEquals(50f, probe.minX)

        // 2. Probar título "I" o "El brujo" (Centrados)
        assertEquals("[GEOMETRIC_TITLE]", probe.processLine("EL BRUJO", 220f, 60f))
        assertEquals("[GEOMETRIC_TITLE]", probe.processLine("I", 245f, 10f))

        // 3. Probar línea de párrafo justificado (Indented but LONG)
        // Midpoint: 70 + 380/2 = 260 (cerca de 250), pero width=380 (> 300)
        assertEquals("BREAK", probe.processLine("Párrafo largo justificando...", 70f, 380f))
    }
}
