package com.example.audiobook.feature_reader.presentation

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onBackClick: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            // Hide TopBar in Reading Mode for immersion
            if (!uiState.isReadingMode) {
                TopAppBar(
                    title = { Text(text = uiState.book?.title ?: "Leyendo...") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleReadingMode() }) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Modo Lectura"
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            // Floating button to exit Reading Mode if TopBar is hidden
            if (uiState.isReadingMode) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = { viewModel.toggleReadingMode() },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                ) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = "Salir Modo Lectura")
                }
            } else { // Show play button when not in reading mode
                androidx.compose.material3.FloatingActionButton(
                    onClick = { viewModel.playAudio() },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir Audio")
                }
            }
        }
    ) { innerPadding ->
        val configuration = LocalConfiguration.current
        val screenWidth = configuration.screenWidthDp.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Gesture Detector
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val width = size.width
                            val x = offset.x
                            
                            // Logic: 30% Left -> Prev, 30% Right -> Next
                            if (x < width * 0.3) {
                                viewModel.previousPage()
                            } else if (x > width * 0.7) {
                                viewModel.nextPage()
                            } else {
                                // Center tap: Toggle Reading Mode (Optional, but intuitive)
                                viewModel.toggleReadingMode()
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        change.consume()
                        // Simple threshold for swipe
                        if (dragAmount < -50) { // Swipe Left -> Next Page
                            viewModel.nextPage()
                        } else if (dragAmount > 50) { // Swipe Right -> Prev Page
                            viewModel.previousPage()
                        }
                    }
                }
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null) {
                Text(
                    text = uiState.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                // The Book Text
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = uiState.currentPageText,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.5 // Better readability
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Page Indicator
                    Text(
                        text = "Página ${uiState.book?.currentPage?.plus(1)} de ${uiState.book?.totalPages}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(100.dp)) // Space for FAB/BottomBar
                }
            }
        }
    }
}
