package com.example.cititor.presentation.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        floatingActionButton = {
            if (!state.isLoading) {
                FloatingActionButton(onClick = { viewModel.startReading() }) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start Reading")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                        .verticalScroll(scrollState)
                ) {
                    val annotatedText = buildAnnotatedString {
                        append(state.pageContent)
                        state.highlightedTextRange?.let {
                            addStyle(
                                style = SpanStyle(background = Color.Yellow),
                                start = it.first,
                                end = it.last
                            )
                        }
                    }
                    Text(text = annotatedText)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { viewModel.previousPage() }, enabled = state.currentPage > 0) {
                        Text("Previous")
                    }
                    Button(onClick = { viewModel.nextPage() }, enabled = state.book?.let { state.currentPage < it.totalPages - 1 } ?: false) {
                        Text("Next")
                    }
                }
            }
        }
    }
}
