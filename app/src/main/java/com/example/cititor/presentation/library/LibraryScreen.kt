package com.example.cititor.presentation.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Delete
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.cititor.presentation.navigation.Screen

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Botón de diagnóstico al inicio
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = { viewModel.runDiagnosticTests() }
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null)
                        Text("  Run Diagnostic Tests")
                    }
                }
            }
            
            items(state.books) { book ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { 
                            navController.navigate(Screen.ReaderScreen.withArgs(book.id))
                        }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = book.title,
                        modifier = Modifier.weight(1f),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                    )
                    
                    androidx.compose.material3.IconButton(
                        onClick = { bookToDelete = book }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete book",
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
