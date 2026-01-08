package com.example.cititor.domain.theme

import kotlinx.serialization.Serializable

/**
 * Representa el tema visual de un libro extraído de su CSS.
 * Contiene fuentes personalizadas y estilos para etiquetas HTML y clases CSS.
 */
@Serializable
data class BookTheme(
    /**
     * Mapa de fuentes personalizadas.
     * Key: Nombre de la fuente en CSS (ej: "Ringbearer")
     * Value: Ruta absoluta al archivo .ttf en el cache
     */
    val fonts: Map<String, String> = emptyMap(),
    
    /**
     * Estilos para etiquetas HTML (h1, h2, p, etc.)
     * Key: Nombre de la etiqueta (ej: "h1")
     * Value: Estilo a aplicar
     */
    val tagStyles: Map<String, CssTextStyle> = emptyMap(),
    
    /**
     * Estilos para clases CSS (.capital, .poema, etc.)
     * Key: Nombre de la clase sin el punto (ej: "capital")
     * Value: Estilo a aplicar
     */
    val classStyles: Map<String, CssTextStyle> = emptyMap()
) {
    companion object {
        /**
         * Tema por defecto cuando no hay CSS disponible
         */
        val DEFAULT = BookTheme()
    }
    
    /**
     * Resuelve el estilo para una etiqueta HTML específica
     */
    fun getStyleForTag(tag: String): CssTextStyle? = tagStyles[tag.lowercase()]
    
    /**
     * Resuelve el estilo para una clase CSS específica
     */
    fun getStyleForClass(className: String): CssTextStyle? = classStyles[className.lowercase()]
    
    /**
     * Obtiene la ruta de una fuente por su nombre
     */
    fun getFontPath(fontFamily: String): String? {
        // Buscar coincidencia exacta primero
        fonts[fontFamily]?.let { return it }
        
        // Buscar coincidencia case-insensitive
        return fonts.entries.firstOrNull { 
            it.key.equals(fontFamily, ignoreCase = true) 
        }?.value
    }
}

/**
 * Representa un estilo de texto extraído del CSS.
 * Todas las propiedades son opcionales para permitir herencia/cascada.
 */
@Serializable
data class CssTextStyle(
    /**
     * Familia de fuente (ej: "Ringbearer,Times")
     * Puede contener múltiples fuentes separadas por coma (fallbacks)
     */
    val fontFamily: String? = null,
    
    /**
     * Tamaño de fuente en unidades 'em' (relativo al tamaño base)
     * Ej: 1.8 para "1.8em"
     */
    val fontSizeEm: Float? = null,
    
    /**
     * Peso de la fuente: "normal", "bold", "100"-"900"
     */
    val fontWeight: String? = null,
    
    /**
     * Estilo de fuente: "normal", "italic", "oblique"
     */
    val fontStyle: String? = null,
    
    /**
     * Alineación del texto: "left", "right", "center", "justify"
     */
    val textAlign: String? = null,
    
    /**
     * Altura de línea en unidades 'em'
     */
    val lineHeightEm: Float? = null,
    
    /**
     * Color del texto en formato CSS (ej: "#000", "rgb(0,0,0)")
     */
    val color: String? = null,
    
    /**
     * Indentación del texto en unidades 'em'
     */
    val textIndentEm: Float? = null,
    
    /**
     * Margen superior en unidades 'em' o porcentaje
     */
    val marginTop: String? = null,
    
    /**
     * Padding superior en unidades 'em'
     */
    val paddingTop: String? = null
) {
    /**
     * Combina este estilo con otro, dando prioridad a las propiedades no nulas de este
     */
    fun mergeWith(other: CssTextStyle): CssTextStyle {
        return CssTextStyle(
            fontFamily = this.fontFamily ?: other.fontFamily,
            fontSizeEm = this.fontSizeEm ?: other.fontSizeEm,
            fontWeight = this.fontWeight ?: other.fontWeight,
            fontStyle = this.fontStyle ?: other.fontStyle,
            textAlign = this.textAlign ?: other.textAlign,
            lineHeightEm = this.lineHeightEm ?: other.lineHeightEm,
            color = this.color ?: other.color,
            textIndentEm = this.textIndentEm ?: other.textIndentEm,
            marginTop = this.marginTop ?: other.marginTop,
            paddingTop = this.paddingTop ?: other.paddingTop
        )
    }
    
    /**
     * Extrae el primer nombre de fuente de la lista de fallbacks
     * Ej: "Ringbearer,Times" -> "Ringbearer"
     */
    fun getPrimaryFontFamily(): String? {
        return fontFamily?.split(",")?.firstOrNull()?.trim()?.removeSurrounding("\"")
    }

    companion object {
        val EMPTY = CssTextStyle()
    }
}
