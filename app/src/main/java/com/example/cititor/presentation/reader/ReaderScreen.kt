package com.example.cititor.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        var cumulativeLength = 0
                        state.pageSegments.forEach { segment ->
                            if (segment is com.example.cititor.domain.model.NarrationSegment) {
                                val isChapter = segment.style == com.example.cititor.domain.model.NarrationStyle.CHAPTER_INDICATOR
                                val segmentText = segment.text
                                
                                val annotatedText = buildAnnotatedString {
                                    append(segmentText)
                                    state.highlightedTextRange?.let { globalRange ->
                                        val segmentStart = cumulativeLength
                                        val segmentEnd = cumulativeLength + segmentText.length
                                        
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
                                
                                Text(
                                    text = annotatedText,
                                    modifier = Modifier.fillMaxWidth().padding(
                                        vertical = if (isChapter) 16.dp else 4.dp
                                    ),
                                    textAlign = if (isChapter) TextAlign.Center else TextAlign.Justify,
                                    style = if (isChapter) {
                                        MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                    } else {
                                        MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Normal
                                        )
                                    }
                                )
                                cumulativeLength += segmentText.length
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
