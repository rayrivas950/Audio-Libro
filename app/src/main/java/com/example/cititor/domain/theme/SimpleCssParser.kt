package com.example.cititor.domain.theme

/**
 * Parser simple de CSS enfocado en extraer estilos relevantes para libros EPUB.
 * No es un parser CSS completo, solo maneja los casos comunes en EPUBs.
 */
class SimpleCssParser {
    
    /**
     * Parsea un archivo CSS y retorna un BookTheme
     */
    fun parse(cssContent: String): BookTheme {
        val fonts = extractFonts(cssContent)
        val rules = extractRules(cssContent)
        
        val tagStyles = mutableMapOf<String, CssTextStyle>()
        val classStyles = mutableMapOf<String, CssTextStyle>()
        
        rules.forEach { rule ->
            val style = parseProperties(rule.properties)
            
            when {
                // Selector de etiqueta (h1, p, body)
                rule.selector.matches(Regex("^[a-z][a-z0-9]*$")) -> {
                    tagStyles[rule.selector] = style
                }
                // Selector de clase (.capital, .poema)
                rule.selector.startsWith(".") -> {
                    val className = rule.selector.removePrefix(".").trim()
                    classStyles[className] = style
                }
                // Ignorar selectores complejos por ahora
                else -> {
                    // Silently ignore complex selectors
                }
            }
        }
        
        return BookTheme(
            fonts = fonts,
            tagStyles = tagStyles,
            classStyles = classStyles
        )
    }
    
    /**
     * Extrae declaraciones @font-face del CSS
     * Retorna mapa: "Ringbearer" -> "../Fonts/RINGM_.TTF"
     */
    private fun extractFonts(css: String): Map<String, String> {
        val fonts = mutableMapOf<String, String>()
        
        // Pattern para @font-face{...}
        val fontFacePattern = Regex("""@font-face\s*\{([^}]+)\}""", RegexOption.IGNORE_CASE)
        
        fontFacePattern.findAll(css).forEach { match ->
            val block = match.groupValues[1]
            
            // Extraer font-family
            val familyMatch = Regex("""font-family\s*:\s*["']?([^;"']+)["']?""", RegexOption.IGNORE_CASE)
                .find(block)
            val family = familyMatch?.groupValues?.get(1)?.trim()
            
            // Extraer src:url(...)
            val srcMatch = Regex("""src\s*:\s*url\(([^)]+)\)""", RegexOption.IGNORE_CASE)
                .find(block)
            val src = srcMatch?.groupValues?.get(1)?.trim()?.removeSurrounding("\"", "'")
            
            if (family != null && src != null) {
                fonts[family] = src
            }
        }
        
        return fonts
    }
    
    /**
     * Extrae reglas CSS (selector { properties })
     */
    private fun extractRules(css: String): List<CssRule> {
        val rules = mutableListOf<CssRule>()
        
        // Remover @font-face para evitar confusión
        val cleanCss = css.replace(Regex("""@font-face\s*\{[^}]+\}""", RegexOption.IGNORE_CASE), "")
        
        // Pattern para selector { properties }
        val rulePattern = Regex("""([^{]+)\{([^}]+)\}""")
        
        rulePattern.findAll(cleanCss).forEach { match ->
            val selector = match.groupValues[1].trim()
            val propertiesBlock = match.groupValues[2]
            
            // Ignorar selectores vacíos o comentarios
            if (selector.isNotBlank() && !selector.startsWith("/*")) {
                rules.add(CssRule(selector, propertiesBlock))
            }
        }
        
        return rules
    }
    
    /**
     * Parsea un bloque de propiedades CSS en un CssTextStyle
     */
    private fun parseProperties(propertiesBlock: String): CssTextStyle {
        val props = mutableMapOf<String, String>()
        
        // Split por ; y parsear cada propiedad
        propertiesBlock.split(";").forEach { prop ->
            val parts = prop.split(":", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim().lowercase()
                val value = parts[1].trim()
                props[key] = value
            }
        }
        
        return CssTextStyle(
            fontFamily = props["font-family"],
            fontSizeEm = parseEmValue(props["font-size"]),
            fontWeight = props["font-weight"],
            fontStyle = props["font-style"],
            textAlign = props["text-align"],
            lineHeightEm = parseEmValue(props["line-height"]),
            color = props["color"],
            textIndentEm = parseEmValue(props["text-indent"]),
            marginTop = props["margin-top"],
            paddingTop = props["padding-top"]
        )
    }
    
    /**
     * Convierte un valor CSS con unidad 'em' a Float
     * Ej: "1.8em" -> 1.8, "300%" -> 3.0
     */
    private fun parseEmValue(value: String?): Float? {
        if (value == null) return null
        
        return when {
            value.endsWith("em", ignoreCase = true) -> {
                value.removeSuffix("em").trim().toFloatOrNull()
            }
            value.endsWith("%") -> {
                value.removeSuffix("%").trim().toFloatOrNull()?.div(100f)
            }
            else -> value.toFloatOrNull()
        }
    }
    
    /**
     * Representa una regla CSS parseada
     */
    private data class CssRule(
        val selector: String,
        val properties: String
    )
}
