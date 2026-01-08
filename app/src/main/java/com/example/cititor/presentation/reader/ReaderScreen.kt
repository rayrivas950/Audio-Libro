package com.example.cititor.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.io.File
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

import androidx.compose.ui.text.style.TextIndent
import androidx.compose.material3.Slider
import androidx.compose.material3.Divider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        floatingActionButton = {
            if (!state.isLoading && !state.isProcessing && state.processingError == null) {
                FloatingActionButton(onClick = { viewModel.startReading() }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start Reading")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.isLoading || state.isProcessing) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    if (state.isProcessing) {
                        Text(
                            text = state.pageDisplayText,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else if (state.processingError != null) {
                Text(
                    text = state.processingError ?: "An unknown error occurred.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    val scrollState = rememberScrollState()
                    
                    // 1. Cargamos las fuentes dinámicamente desde los archivos extraídos
                    val fontFamilies = remember(state.bookTheme) {
                        state.bookTheme.fonts.mapValues { (family, path) ->
                            try {
                                val file = File(path)
                                if (file.exists()) {
                                    FontFamily(Font(file))
                                } else {
                                    android.util.Log.w("ReaderScreen", "Font file not found: $path")
                                    FontFamily.Default
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ReaderScreen", "Error loading font $family from $path", e)
                                FontFamily.Default
                            }
                        }
                    }

                    // 2. Helper para resolver estilo basado en la etiqueta
                    fun getTextStyle(segment: com.example.cititor.domain.model.TextSegment): com.example.cititor.domain.theme.CssTextStyle {
                        val theme = state.bookTheme
                        return when (segment) {
                            is com.example.cititor.domain.model.NarrationSegment -> {
                                when (segment.style) {
                                    com.example.cititor.domain.model.NarrationStyle.TITLE_LARGE -> theme.tagStyles["h1"] ?: theme.tagStyles["h2"] ?: com.example.cititor.domain.theme.CssTextStyle.EMPTY
                                    com.example.cititor.domain.model.NarrationStyle.TITLE_MEDIUM -> theme.tagStyles["h3"] ?: theme.tagStyles["h4"] ?: com.example.cititor.domain.theme.CssTextStyle.EMPTY
                                    else -> theme.tagStyles["p"] ?: theme.tagStyles["body"] ?: com.example.cititor.domain.theme.CssTextStyle.EMPTY
                                }
                            }
                            else -> theme.tagStyles["p"] ?: theme.tagStyles["body"] ?: com.example.cititor.domain.theme.CssTextStyle.EMPTY
                        }
                    }

                    // Determine if the page is "image-centric" (low text, at least one image)
                    val totalWords = state.pageSegments.filterIsInstance<com.example.cititor.domain.model.NarrationSegment>().sumOf { s -> 
                        s.text.split(Regex("\\s+")).count { it.isNotBlank() } 
                    } + state.pageSegments.filterIsInstance<com.example.cititor.domain.model.DialogueSegment>().sumOf { s -> 
                        s.text.split(Regex("\\s+")).count { it.isNotBlank() } 
                    }
                    val hasImage = state.pageSegments.any { it is com.example.cititor.domain.model.ImageSegment }
                    val hasLargeTitle = state.pageSegments.any { it is com.example.cititor.domain.model.NarrationSegment && it.style == com.example.cititor.domain.model.NarrationStyle.TITLE_LARGE }
                    val isImageCentric = hasImage && totalWords < 30
                    val isTitleCentric = hasLargeTitle && totalWords < 15
                    val isInmersive = isImageCentric || isTitleCentric
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = if (isInmersive) Arrangement.Center else Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        var cumulativeLength = 0
                        state.pageSegments.forEach { segment ->
                            val cssStyle = getTextStyle(segment)
                            val fontFamily = cssStyle.getPrimaryFontFamily()?.let { fontFamilies[it] } ?: FontFamily.Default

                            when (segment) {
                                 is com.example.cititor.domain.model.ImageSegment -> {
                                    // Clean any potential whitespace and reconstruct full path
                                    val cleanPathStored = segment.imagePath.replace("\\s".toRegex(), "")
                                    val imagePath = if (cleanPathStored.contains("/")) {
                                        cleanPathStored
                                    } else {
                                        "${LocalContext.current.cacheDir.absolutePath}/book_images/$cleanPathStored"
                                    }
                                    
                                    android.util.Log.d("ReaderScreen", "📷 ImageSegment: stored='${segment.imagePath}', cleaned='$cleanPathStored', loading='$imagePath', isInmersive=$isInmersive")
                                    
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = if (isInmersive) 24.dp else 12.dp),
                                        elevation = CardDefaults.cardElevation(defaultElevation = if (isInmersive) 8.dp else 4.dp),
                                        shape = MaterialTheme.shapes.medium,
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f) // darken slightly or use variant
                                        )
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            coil.compose.AsyncImage(
                                                model = java.io.File(imagePath),
                                                contentDescription = segment.caption,
                                                modifier = if (isInmersive) {
                                                    Modifier
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .sizeIn(maxHeight = 425.dp) // Reduced by 15% (was 500.dp)
                                                } else {
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .aspectRatio(1.5f)
                                                },
                                                contentScale = ContentScale.Fit,
                                                onError = { state ->
                                                    android.util.Log.e("ReaderScreen", "❌ AsyncImage FAILED to load: $imagePath", state.result.throwable)
                                                },
                                                onLoading = {
                                                    android.util.Log.d("ReaderScreen", "⏳ AsyncImage loading: $imagePath")
                                                },
                                                onSuccess = {
                                                    android.util.Log.d("ReaderScreen", "✅ AsyncImage success: $imagePath")
                                                }
                                            )
                                            
                                            if (!segment.caption.isNullOrBlank()) {
                                                Text(
                                                    text = segment.caption,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    modifier = Modifier.padding(8.dp),
                                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                )
                                            }
                                        }
                                    }
                                    cumulativeLength += segment.text.length
                                }
                                 is com.example.cititor.domain.model.TextSegment -> {
                                    // Handle Narration and Dialogue (both share basics)
                                    val style = if (segment is com.example.cititor.domain.model.NarrationSegment) segment.style else com.example.cititor.domain.model.NarrationStyle.NEUTRAL
                                    val isTitle = style == com.example.cititor.domain.model.NarrationStyle.TITLE_LARGE || 
                                                 style == com.example.cititor.domain.model.NarrationStyle.TITLE_MEDIUM ||
                                                 style == com.example.cititor.domain.model.NarrationStyle.CHAPTER_INDICATOR
                                    
                                    val segmentText = segment.text
                                    
                                    val annotatedText = buildAnnotatedString {
                                        if (segment is com.example.cititor.domain.model.NarrationSegment && !segment.dropCap.isNullOrBlank() && segmentText.startsWith(segment.dropCap)) {
                                            withStyle(style = SpanStyle(fontSize = 3.em, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
                                                append(segmentText.substring(0, 1))
                                            }
                                            append(segmentText.substring(1))
                                        } else {
                                            append(segmentText)
                                        }
                                        
                                        state.highlightedTextRange?.let { globalRange ->
                                            val segmentStart = cumulativeLength
                                            val segmentEnd = cumulativeLength + segmentText.length
                                            
                                            if (segmentStart < segmentEnd) {
                                                val intersectStart = maxOf(globalRange.first, segmentStart)
                                                val intersectEnd = minOf(globalRange.last, segmentEnd)
                                                
                                                if (intersectStart <= intersectEnd) {
                                                    addStyle(
                                                        style = SpanStyle(background = Color.Yellow),
                                                        start = intersectStart - segmentStart,
                                                        end = (intersectEnd - segmentStart) + 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    
                                    Text(
                                        text = annotatedText,
                                        modifier = Modifier.fillMaxWidth().padding(
                                            vertical = if (isTitle) 24.dp else 4.dp
                                        ),
                                        textAlign = when (cssStyle.textAlign?.lowercase()) {
                                            "center" -> TextAlign.Center
                                            "right" -> TextAlign.Right
                                            "justify" -> TextAlign.Justify
                                            else -> if (isTitle) TextAlign.Center else TextAlign.Justify
                                        },
                                        style = when (style) {
                                            com.example.cititor.domain.model.NarrationStyle.TITLE_LARGE -> {
                                                val fontWeight = if (segment is com.example.cititor.domain.model.NarrationSegment && segment.isBold != null) {
                                                    if (segment.isBold) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                                } else {
                                                    val cssWeight = if (cssStyle.fontWeight?.lowercase() == "bold") FontWeight.Bold else FontWeight.Bold
                                                    cssWeight
                                                }
                                                
                                                MaterialTheme.typography.headlineLarge.copy(
                                                    fontWeight = fontWeight,
                                                    fontSize = cssStyle.fontSizeEm?.let { (it * 30).sp } ?: (if (isTitleCentric) 36.sp else 30.sp),
                                                    textAlign = TextAlign.Center,
                                                    fontFamily = fontFamily
                                                )
                                            }
                                            com.example.cititor.domain.model.NarrationStyle.TITLE_MEDIUM -> {
                                                val fontWeight = if (segment is com.example.cititor.domain.model.NarrationSegment && segment.isBold != null) {
                                                    if (segment.isBold) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                                } else {
                                                    val cssWeight = if (cssStyle.fontWeight?.lowercase() == "bold") FontWeight.Bold else FontWeight.Bold
                                                    cssWeight
                                                }
                                                
                                                MaterialTheme.typography.headlineMedium.copy(
                                                    fontWeight = fontWeight,
                                                    textAlign = TextAlign.Center,
                                                    fontSize = cssStyle.fontSizeEm?.let { (it * 24).sp } ?: MaterialTheme.typography.headlineMedium.fontSize,
                                                    fontFamily = fontFamily
                                                )
                                            }
                                            com.example.cititor.domain.model.NarrationStyle.CHAPTER_INDICATOR -> {
                                                MaterialTheme.typography.headlineSmall.copy(
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                    textAlign = TextAlign.Center,
                                                    fontFamily = fontFamily
                                                )
                                            }
                                            else -> {
                                                val fontSize = cssStyle.fontSizeEm?.let { (it * 16).sp } ?: 16.sp
                                                MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = if (cssStyle.fontWeight?.lowercase() == "bold") FontWeight.Bold else FontWeight.Normal,
                                                    textAlign = TextAlign.Justify,
                                                    textIndent = androidx.compose.ui.text.style.TextIndent(
                                                        firstLine = 24.sp,
                                                        restLine = 0.sp
                                                    ),
                                                    fontSize = fontSize,
                                                    fontFamily = fontFamily
                                                )
                                            }
                                        }
                                    )
                                    cumulativeLength += segmentText.length
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Voice Model Selector
                        var showModels by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { showModels = true }) {
                                Icon(Icons.Default.Settings, contentDescription = null)
                                Spacer(Modifier.padding(4.dp))
                                Text("Voz: ${state.currentVoiceModel}")
                            }
                            DropdownMenu(
                                expanded = showModels,
                                onDismissRequest = { showModels = false }
                            ) {
                                listOf("Miro High (ES)").forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model) },
                                        onClick = {
                                            viewModel.setVoiceModel(model)
                                            showModels = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.padding(8.dp))

                        Text(
                            text = "Interpretación: ${"%.1f".format(state.dramatism)}x",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Slider(
                            value = state.dramatism,
                            onValueChange = { viewModel.updateDramatism(it) },
                            valueRange = 0.0f..2.0f,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    Divider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(onClick = { viewModel.previousPage() }, enabled = state.currentPage > 0) {
                            Text("Previous")
                        }
                        Button(
                            onClick = { viewModel.nextPage() },
                            enabled = state.book?.let { state.currentPage < it.totalPages - 1 } ?: false
                        ) {
                            Text("Next")
                        }
                    }
                }
            }
        }
    }
}
