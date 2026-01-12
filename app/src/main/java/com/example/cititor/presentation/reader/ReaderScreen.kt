package com.example.cititor.presentation.reader

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.unit.Constraints
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.cititor.domain.model.*
import com.example.cititor.domain.theme.BookTheme
import com.example.cititor.domain.theme.CssTextStyle
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val textMeasurer = rememberTextMeasurer()
    val fontFamilies = rememberFontFamilies(state.bookTheme)
    val error = state.processingError
    val density = LocalDensity.current
    val fontFamilyResolver = LocalFontFamilyResolver.current

    Scaffold(
        bottomBar = {
            if (!state.isLoading && !state.isProcessing) {
                ReaderBottomControls(
                    state = state,
                    onVoiceChange = viewModel::setVoiceModel,
                    onDramatismChange = viewModel::updateDramatism
                )
            }
        },
        floatingActionButton = {
            if (!state.isLoading && !state.isProcessing && state.processingError == null) {
                FloatingActionButton(onClick = { viewModel.startReading() }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start Reading")
                }
            }
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val constraints = this.constraints
            
            // Paginación Reactiva Multicapítulo Refinada (Continuidad Absoluta)
            LaunchedEffect(state.windowChapters, constraints.maxWidth, constraints.maxHeight, state.bookTheme) {
                if (state.navigationMode == ReaderNavigationMode.PAGINATED && state.windowChapters.isNotEmpty()) {
                    val sortedChapters = state.windowChapters.keys.sorted()
                    val flattenedSegments = mutableListOf<SegmentWithChapter>()
                    
                    for (chIndex in sortedChapters) {
                        val segments = state.windowChapters[chIndex] ?: continue
                        segments.forEach { seg ->
                            flattenedSegments.add(SegmentWithChapter(seg, chIndex))
                        }
                    }
                    
                    val chaptersWithImages = state.windowChapters.entries
                        .filter { it.value.any { seg -> seg is ImageSegment } }
                        .map { it.key }
                        .toSet()
                    
                    val allPages = paginateContentWithChapters(
                        segments = flattenedSegments,
                        maxWidthPx = constraints.maxWidth.toFloat(),
                        maxHeightPx = constraints.maxHeight.toFloat(),
                        textMeasurer = textMeasurer,
                        theme = state.bookTheme,
                        density = density,
                        fontFamilyResolver = fontFamilyResolver,
                        chaptersWithImages = chaptersWithImages
                    )
                    viewModel.setVirtualPages(allPages)
                }
            }

            if (state.isLoading || state.isProcessing) {
                LoadingOverlay(state.pageDisplayText, state.isProcessing)
            } else if (error != null) {
                ErrorDisplay(error)
            } else {
                if (state.navigationMode == ReaderNavigationMode.PAGINATED) {
                    // Encontrar el índice global que corresponde al capítulo y página virtual guardados
                    val initialGlobalPage = remember(state.virtualPages) {
                        val index = state.virtualPages.indexOfFirst { 
                            it.chapterIndex == state.currentPage 
                        }
                        if (index != -1) index else 0
                    }

                    val pagerState = rememberPagerState(
                        initialPage = initialGlobalPage,
                        pageCount = { state.virtualPages.size }
                    )

                    LaunchedEffect(pagerState.currentPage) {
                        viewModel.onVirtualPageChanged(pagerState.currentPage)
                    }

                    LaunchedEffect(state.currentVirtualPage) {
                        if (pagerState.currentPage != state.currentVirtualPage && state.virtualPages.isNotEmpty()) {
                            pagerState.scrollToPage(state.currentVirtualPage)
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.Top,
                        contentPadding = PaddingValues(0.dp)
                    ) { virtualPageIndex ->
                        val pageInfo = state.virtualPages.getOrNull(virtualPageIndex) ?: return@HorizontalPager
                        ReaderContentColumn(
                            segments = pageInfo.segments,
                            theme = state.bookTheme,
                            fontFamilies = fontFamilies,
                            highlightedRange = state.highlightedTextRange,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            cumulativeOffset = calculateChapterCumulativeOffset(state.virtualPages, virtualPageIndex)
                        )
                    }
                } else {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(scrollState)
                    ) {
                        ReaderContentColumn(
                            segments = state.windowChapters[state.currentPage] ?: emptyList(),
                            theme = state.bookTheme,
                            fontFamilies = fontFamilies,
                            highlightedRange = state.highlightedTextRange
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReaderContentColumn(
    segments: List<TextSegment>,
    theme: BookTheme,
    fontFamilies: Map<String, FontFamily>,
    highlightedRange: IntRange? = null,
    cumulativeOffset: Int = 0,
    modifier: Modifier = Modifier
) {
    var localCumulative = cumulativeOffset
    
    // Determine immersion
    val totalWords = segments.filterIsInstance<NarrationSegment>().sumOf { it.text.split(Regex("\\s+")).size }
    val hasImage = segments.any { it is ImageSegment }
    val isImageCentric = hasImage && totalWords < 30
    
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = if (isImageCentric) Arrangement.Center else Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        segments.forEach { segment ->
            val cssStyle = getTextStyle(segment, theme)
            val fontFamily = cssStyle.getPrimaryFontFamily()?.let { fontFamilies[it] } ?: FontFamily.Default

            when (segment) {
                is ImageSegment -> {
                    ImageComponent(segment, isImageCentric)
                }
                is TextSegment -> {
                    TextComponent(
                        segment = segment,
                        cssStyle = cssStyle,
                        fontFamily = fontFamily,
                        highlightedRange = highlightedRange,
                        cumulativeLength = localCumulative
                    )
                    localCumulative += segment.text.length
                }
            }
        }
    }
}

@Composable
fun TextComponent(
    segment: TextSegment,
    cssStyle: CssTextStyle,
    fontFamily: FontFamily,
    highlightedRange: IntRange?,
    cumulativeLength: Int
) {
    val style = if (segment is NarrationSegment) segment.style else NarrationStyle.NEUTRAL
    val isTitle = style == NarrationStyle.TITLE_LARGE || style == NarrationStyle.TITLE_MEDIUM || style == NarrationStyle.CHAPTER_INDICATOR
    
    val annotatedText = buildAnnotatedString {
        val segmentText = segment.text
        if (segment is NarrationSegment && !segment.dropCap.isNullOrBlank() && segmentText.startsWith(segment.dropCap)) {
            withStyle(style = SpanStyle(fontSize = 3.em, fontWeight = FontWeight.Bold)) {
                append(segmentText.substring(0, 1))
            }
            append(segmentText.substring(1))
        } else {
            append(segmentText)
        }
        
        highlightedRange?.let { globalRange ->
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
        modifier = Modifier.fillMaxWidth().padding(vertical = if (isTitle) 24.dp else 4.dp),
        textAlign = when (cssStyle.textAlign?.lowercase()) {
            "center" -> TextAlign.Center
            "right" -> TextAlign.Right
            "justify" -> TextAlign.Justify
            else -> if (isTitle) TextAlign.Center else TextAlign.Justify
        },
        style = when (style) {
            NarrationStyle.TITLE_LARGE -> MaterialTheme.typography.headlineLarge.copy(
                fontWeight = if (segment is NarrationSegment && segment.isBold == false) FontWeight.Normal else FontWeight.Bold,
                fontSize = cssStyle.fontSizeEm?.let { (it * 30).sp } ?: 30.sp,
                fontFamily = fontFamily,
                textAlign = TextAlign.Center,
                hyphens = Hyphens.Auto
            )
            NarrationStyle.TITLE_MEDIUM -> MaterialTheme.typography.headlineMedium.copy(
                fontWeight = if (segment is NarrationSegment && segment.isBold == false) FontWeight.Normal else FontWeight.Bold,
                fontSize = cssStyle.fontSizeEm?.let { (it * 24).sp } ?: 24.sp,
                fontFamily = fontFamily,
                textAlign = TextAlign.Center,
                hyphens = Hyphens.Auto
            )
            else -> {
                val fontSize = cssStyle.fontSizeEm?.let { (it * 16).sp } ?: 16.sp
                MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (cssStyle.fontWeight?.lowercase() == "bold") FontWeight.Bold else FontWeight.Normal,
                    textIndent = TextIndent(firstLine = 24.sp),
                    fontSize = fontSize,
                    fontFamily = fontFamily,
                    textAlign = TextAlign.Justify,
                    hyphens = Hyphens.Auto
                )
            }
        }
    )
}

@Composable
fun ImageComponent(segment: ImageSegment, isImmersive: Boolean) {
    val context = LocalContext.current
    val cleanPathStored = segment.imagePath.replace("\\s".toRegex(), "")
    val imagePath = if (cleanPathStored.contains("/")) cleanPathStored else {
        val f = File(context.filesDir, "book_images/$cleanPathStored")
        if (f.exists()) f.absolutePath else File(context.cacheDir, "book_images/$cleanPathStored").absolutePath
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = if (isImmersive) 24.dp else 12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isImmersive) 8.dp else 4.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            coil.compose.AsyncImage(
                model = File(imagePath),
                contentDescription = segment.caption,
                modifier = Modifier.fillMaxWidth().sizeIn(maxHeight = if (isImmersive) 425.dp else 300.dp),
                contentScale = ContentScale.Fit
            )
            if (!segment.caption.isNullOrBlank()) {
                Text(segment.caption, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(8.dp), fontStyle = FontStyle.Italic)
            }
        }
    }
}

@Composable
fun ReaderBottomControls(
    state: ReaderState,
    onVoiceChange: (String) -> Unit,
    onDramatismChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Voice & Dramatism
        Row(verticalAlignment = Alignment.CenterVertically) {
            var showModels by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { showModels = true }, shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Voz: ${state.currentVoiceModel}", style = MaterialTheme.typography.labelMedium)
                }
                DropdownMenu(expanded = showModels, onDismissRequest = { showModels = false }) {
                    DropdownMenuItem(text = { Text("Miro High (ES)") }, onClick = { onVoiceChange("Miro High (ES)"); showModels = false })
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Cap. ${state.currentPage + 1} | Pág. ${state.currentVirtualPage + 1}/${state.virtualPages.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Slider(
            value = state.dramatism,
            onValueChange = onDramatismChange,
            valueRange = 0f..2f,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun LoadingOverlay(text: String, isProcessing: Boolean) {
    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        if (isProcessing) Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
    }
}

@Composable
fun ErrorDisplay(error: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
    }
}

private fun getTextStyle(segment: TextSegment, theme: BookTheme): CssTextStyle {
    return when (segment) {
        is NarrationSegment -> when (segment.style) {
            NarrationStyle.TITLE_LARGE -> theme.tagStyles["h1"] ?: theme.tagStyles["h2"] ?: CssTextStyle.EMPTY
            NarrationStyle.TITLE_MEDIUM -> theme.tagStyles["h3"] ?: theme.tagStyles["h4"] ?: CssTextStyle.EMPTY
            else -> theme.tagStyles["p"] ?: theme.tagStyles["body"] ?: CssTextStyle.EMPTY
        }
        else -> theme.tagStyles["p"] ?: theme.tagStyles["body"] ?: CssTextStyle.EMPTY
    }
}

@Composable
private fun rememberFontFamilies(theme: BookTheme): Map<String, FontFamily> {
    return remember(theme) {
        theme.fonts.mapValues { (_, path) ->
            try {
                val f = File(path)
                if (f.exists()) FontFamily(Font(f)) else FontFamily.Default
            } catch (e: Exception) {
                FontFamily.Default
            }
        }
    }
}

private fun calculateChapterCumulativeOffset(pages: List<VirtualPageInfo>, upTo: Int): Int {
    val targetChapter = pages.getOrNull(upTo)?.chapterIndex ?: return 0
    var sum = 0
    for (i in 0 until upTo) {
        val p = pages[i]
        if (p.chapterIndex == targetChapter) {
            sum += p.segments.sumOf { (it as? TextSegment)?.text?.length ?: 0 }
        }
    }
    return sum
}

private data class SegmentWithChapter(
    val segment: TextSegment,
    val chapterIndex: Int
)

private fun paginateContentWithChapters(
    segments: List<SegmentWithChapter>,
    maxWidthPx: Float,
    maxHeightPx: Float,
    textMeasurer: TextMeasurer,
    theme: BookTheme,
    density: androidx.compose.ui.unit.Density,
    fontFamilyResolver: androidx.compose.ui.text.font.FontFamily.Resolver,
    chaptersWithImages: Set<Int> = emptySet()
): List<VirtualPageInfo> {
    val pages = mutableListOf<VirtualPageInfo>()
    var currPageSegments = mutableListOf<TextSegment>()
    var currChapterIndex = segments.firstOrNull()?.chapterIndex ?: 0
    var h = 0f
    val safeBottomPadding = with(density) { 40.dp.toPx() }
    
    val workList = segments.toMutableList()
    var i = 0
    
    while (i < workList.size) {
        val entry = workList[i]
        val seg = entry.segment

        // Forzar salto de página en títulos grandes
        val isBreak = seg is NarrationSegment && seg.style == NarrationStyle.TITLE_LARGE
        
        // --- AISLAMIENTO SEMÁNTICO DE IMÁGENES ---
        // Si el capítulo físico cambia, comprobamos si alguno de los dos involucrados tiene imágenes.
        // Si es así, forzamos un salto de página para mantener la "estructura original" de esa página física.
        val chapterChanged = entry.chapterIndex != currChapterIndex
        val isImageBoundary = chapterChanged && (chaptersWithImages.contains(currChapterIndex) || chaptersWithImages.contains(entry.chapterIndex))

        if ((isBreak || isImageBoundary) && currPageSegments.isNotEmpty()) {
            pages.add(VirtualPageInfo(currPageSegments, currChapterIndex))
            currPageSegments = mutableListOf()
            h = 0f
            currChapterIndex = entry.chapterIndex
        }
        
        // Si la página está vacía, adoptamos el capítulo del segmento actual
        if (currPageSegments.isEmpty()) {
            currChapterIndex = entry.chapterIndex
        }

        val fontSize = when (seg) {
            is NarrationSegment -> when (seg.style) {
                NarrationStyle.TITLE_LARGE -> 30f
                NarrationStyle.TITLE_MEDIUM -> 24f
                else -> 18f
            }
            else -> 18f
        }

        val estHeight = when (seg) {
            is ImageSegment -> 400f
            is TextSegment -> {
                textMeasurer.measure(
                    text = seg.text,
                    style = TextStyle(fontSize = fontSize.sp),
                    constraints = Constraints(maxWidth = maxWidthPx.toInt()),
                    density = density,
                    fontFamilyResolver = fontFamilyResolver
                ).size.height.toFloat() + 20f
            }
            else -> 100f
        }
        
        val remainingH = maxHeightPx - safeBottomPadding - h
        
        if (estHeight > remainingH && currPageSegments.isNotEmpty() && remainingH > 100f && seg is NarrationSegment) {
            // --- SEGMENT SPLITTING ---
            val layoutResult = textMeasurer.measure(
                text = seg.text,
                style = TextStyle(fontSize = fontSize.sp),
                constraints = Constraints(maxWidth = maxWidthPx.toInt()),
                density = density,
                fontFamilyResolver = fontFamilyResolver
            )
            
            var lastFittingLine = -1
            for (lineIdx in 0 until layoutResult.lineCount) {
                if (layoutResult.getLineBottom(lineIdx) < remainingH - 20f) {
                    lastFittingLine = lineIdx
                } else {
                    break
                }
            }
            
            if (lastFittingLine >= 0) {
                val offset = layoutResult.getLineEnd(lastFittingLine)
                if (offset > 0 && offset < seg.text.length) {
                    val part1Text = seg.text.substring(0, offset)
                    val part2Text = seg.text.substring(offset).trimStart()
                    
                    val part1 = seg.copy(text = part1Text)
                    val part2 = seg.copy(text = part2Text).let { 
                        if (it is NarrationSegment) it.copy(dropCap = null) else it 
                    }
                    
                    currPageSegments.add(part1)
                    pages.add(VirtualPageInfo(currPageSegments, currChapterIndex))
                    currPageSegments = mutableListOf()
                    h = 0f
                    
                    workList[i] = entry.copy(segment = part2)
                    continue 
                }
            }
        }

        if (h + estHeight > maxHeightPx - safeBottomPadding && currPageSegments.isNotEmpty()) {
            pages.add(VirtualPageInfo(currPageSegments, currChapterIndex))
            currPageSegments = mutableListOf()
            h = 0f
            currChapterIndex = entry.chapterIndex
        }
        
        currPageSegments.add(seg)
        h += estHeight
        i++
    }
    
    if (currPageSegments.isNotEmpty()) {
        pages.add(VirtualPageInfo(currPageSegments, currChapterIndex))
    }
    
    return pages
}

private fun paginateContent(
    segments: List<TextSegment>,
    maxWidthPx: Float,
    maxHeightPx: Float,
    textMeasurer: TextMeasurer,
    theme: BookTheme,
    density: androidx.compose.ui.unit.Density,
    fontFamilyResolver: androidx.compose.ui.text.font.FontFamily.Resolver
): List<List<TextSegment>> {
    // Keep this for backward compatibility if needed, or just let it call the new one with a dummy chapterIndex
    return paginateContentWithChapters(
        segments.map { SegmentWithChapter(it, 0) },
        maxWidthPx, maxHeightPx, textMeasurer, theme, density, fontFamilyResolver
    ).map { it.segments }
}
