package com.example.cititor.presentation.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cititor.data.text_extractor.ExtractorFactory
import com.example.cititor.debug.DebugHelper
import com.example.cititor.domain.model.Book
import com.example.cititor.domain.use_case.AddBookUseCase
import com.example.cititor.domain.use_case.DeleteBookUseCase
import com.example.cititor.domain.use_case.GetBooksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject

/**
 * The state holder for the Library screen.
 */
data class LibraryState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getBooksUseCase: GetBooksUseCase,
    private val addBookUseCase: AddBookUseCase,
    private val deleteBookUseCase: DeleteBookUseCase,
    private val extractorFactory: ExtractorFactory,
    private val coverExtractor: com.example.cititor.core.utils.CoverExtractor,
    private val application: Application,
    private val json: Json
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state

    init {
        getBooks()
    }
    
    /**
     * Ejecuta pruebas de diagnóstico para identificar problemas
     */
    fun runDiagnosticTests() {
        viewModelScope.launch {
            // Test 1: Serialización
            DebugHelper.testSerialization(application, json)
            
            // Test 2: TextAnalyzer
            DebugHelper.testTextAnalyzer(application, json)
        }
    }

    fun onBookUriSelected(uri: Uri) {
        viewModelScope.launch {
            // Persist permission for the URI
            val contentResolver = application.contentResolver
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            parseBook(uri)
        }
    }

    private suspend fun parseBook(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                val extractor = extractorFactory.create(uri.toString())
                if (extractor == null) {
                    // Unsupported format
                    return@withContext
                }

                // Get total pages using Tika
                val totalPages = extractor.getPageCount(application, uri)
                
                // Extract filename as title (Tika doesn't provide metadata extraction easily)
                val fileName = getFileName(uri) ?: "Untitled Book"
                val title = fileName.substringBeforeLast('.')
                
                // Extract Cover
                val isPdf = uri.toString().lowercase().endsWith(".pdf") || 
                           application.contentResolver.getType(uri)?.contains("pdf") == true
                val coverPath = coverExtractor.extractCover(application, uri, isPdf)
                
                val newBook = Book(
                    id = 0, // Room will auto-generate
                    title = title,
                    author = null, // Metadata extraction would require format-specific logic
                    filePath = uri.toString(),
                    coverPath = coverPath,
                    currentPage = 0,
                    totalPages = totalPages,
                    lastReadTimestamp = System.currentTimeMillis(),
                    processingWorkId = null
                )
                addBookUseCase(newBook)

            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        if (uri.scheme == "content") {
            val cursor = application.contentResolver.query(
                uri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val displayNameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (displayNameIndex != -1) {
                        return it.getString(displayNameIndex)
                    }
                }
            }
        }
        return uri.path?.substringAfterLast('/')
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            // Delete cover file if exists
            book.coverPath?.let { path ->
                try {
                    val file = java.io.File(path)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            deleteBookUseCase(book)
        }
    }

    private fun getBooks() {
        getBooksUseCase().onEach { books ->
            _state.value = state.value.copy(
                books = books
            )
        }.launchIn(viewModelScope)
    }
}
