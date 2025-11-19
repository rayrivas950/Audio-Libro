package com.example.audiobook.feature_library.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LibraryScreen(
    onBookClick: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    // 1. Observe the State (The "Brain" tells us what to draw)
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val books by viewModel.books.collectAsState()

    // 2. Setup the File Picker Launcher
    // This is the "Door" to the Android System File Picker
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        // When the user picks a file (or cancels), this code runs.
        uri?.let { 
            // We need to persist permission to read this file later (e.g. after reboot)
            val flag = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flag)
            
            viewModel.onFilePicked(it) 
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            // Simple header for now
            Box(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Mi Biblioteca",
                    style = MaterialTheme.typography.headlineMedium
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 3. React to State Changes
            
            if (uiState.isLoading) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Analizando archivo...")
            } else {
                // Show the "Pick File" button
                Button(onClick = {
                    // Launch the system picker for PDF and EPUB
                    launcher.launch(arrayOf("application/pdf", "application/epub+zip"))
                }) {
                    Text("Importar Nuevo Libro")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Show Result
            uiState.importedBookTitle?.let { title ->
                Text(
                    text = "Libro Importado: $title",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Show Error
            uiState.error?.let { errorMsg ->
                Text(
                    text = errorMsg,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { viewModel.clearError() }) {
                    Text("Entendido")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Mis Libros",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (books.isEmpty()) {
            Text(text = "No tienes libros guardados aún.", style = MaterialTheme.typography.bodyLarge)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(books) { book ->
                    BookItem(book = book, onClick = { onBookClick(book.id) })
                }
            }
        }
    }
}

@Composable
fun BookItem(
    book: com.example.audiobook.core.database.entity.BookEntity,
    onClick: () -> Unit
) {
    androidx.compose.material3.Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = book.title, style = MaterialTheme.typography.titleMedium)
            Text(text = "${book.totalPages} páginas", style = MaterialTheme.typography.bodySmall)
        }
    }
}
