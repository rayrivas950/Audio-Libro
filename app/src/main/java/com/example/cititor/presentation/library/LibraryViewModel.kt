package com.example.cititor.presentation.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cititor.domain.model.Book
import com.example.cititor.domain.use_case.AddBookUseCase
import com.example.cititor.domain.use_case.GetBooksUseCase
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val application: Application
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state

    init {
        PDFBoxResourceLoader.init(application)
        getBooks()
    }

    fun onBookUriSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                // Take persistable URI permission
                val contentResolver = application.contentResolver
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

                val document = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use {
                        PDDocument.load(it)
                    }
                }
                document?.let {
                    val newBook = Book(
                        id = 0, // Room will auto-generate
                        title = it.documentInformation.title ?: "Untitled",
                        author = it.documentInformation.author,
                        filePath = uri.toString(),
                        coverPath = null, // Future feature
                        currentPage = 0,
                        totalPages = it.numberOfPages,
                        lastReadTimestamp = System.currentTimeMillis()
                    )
                    addBookUseCase(newBook)
                    it.close()
                }
            } catch (e: IOException) {
                // Handle exception (e.g., show a toast to the user)
                e.printStackTrace()
            } catch (e: SecurityException) {
                // Handle exception
                e.printStackTrace()
            }
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
