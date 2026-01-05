package com.example.cititor.presentation.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Delete
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.cititor.presentation.navigation.Screen
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage

@Composable
fun LibraryScreen(
    navController: NavController,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var bookToDelete by remember { mutableStateOf<com.example.cititor.domain.model.Book?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onBookUriSelected(it) }
    }

    // Confirmation Dialog
    bookToDelete?.let { book ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text("¿Eliminar libro?") },
            text = { Text("¿Estás seguro de que quieres eliminar '${book.title}'? Esta acción borrará todos tus datos procesados.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel.deleteBook(book)
                        bookToDelete = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { bookToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { filePickerLauncher.launch(arrayOf("application/pdf", "application/epub+zip")) }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add book")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Diagnostic Button
            Button(
                onClick = { viewModel.runDiagnosticTests() },
                modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally)
            ) {
                Icon(Icons.Default.Build, contentDescription = null)
                Text("  Run Diagnostic Tests")
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.books) { book ->
                    BookCard(
                        book = book,
                        onClick = { navController.navigate(Screen.ReaderScreen.withArgs(book.id)) },
                        onDelete = { bookToDelete = book }
                    )
                }
            }
        }
    }
}

@Composable
fun BookCard(
    book: com.example.cititor.domain.model.Book,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "scaleAnimation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Disable default ripple to emphasize the scale effect
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f) // Common book aspect ratio
            ) {
                if (book.coverPath != null) {
                    AsyncImage(
                        model = book.coverPath,
                        contentDescription = "Cover of ${book.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Placeholder for books without covers
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.LightGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = book.title.take(1).uppercase(),
                            style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
                            color = androidx.compose.ui.graphics.Color.Gray
                        )
                    }
                }
                
                // Overlay Delete Icon (Subtle)
                androidx.compose.material3.IconButton(
                    onClick = onDelete,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete book",
                        tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.background(
                            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ).padding(4.dp)
                    )
                }
            }
            
            Text(
                text = book.title,
                modifier = Modifier.padding(8.dp),
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
