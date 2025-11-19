package com.example.audiobook.feature_reader.presentation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.core.common.Resource
import com.example.audiobook.core.database.entity.BookEntity
import com.example.audiobook.feature_library.domain.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val book: BookEntity? = null,
    val currentPageText: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val isReadingMode: Boolean = false // Toggle for "Reading Only Mode"
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val repository: LibraryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    // Get the bookId passed from the navigation
    private val bookId: Long = checkNotNull(savedStateHandle["bookId"])

    init {
        loadBook()
    }

    private fun loadBook() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val book = repository.getBookById(bookId)
            if (book == null) {
                _uiState.update { it.copy(isLoading = false, error = "Libro no encontrado") }
                return@launch
            }

            _uiState.update { it.copy(book = book) }
            loadPage(book.fileUri, book.currentPage)
        }
    }

    private fun loadPage(uriString: String, pageIndex: Int) {
        viewModelScope.launch {
            val uri = Uri.parse(uriString)
            when (val result = repository.getBookPage(uri, pageIndex)) {
                is Resource.Success -> {
                    _uiState.update { 
                        it.copy(isLoading = false, currentPageText = result.data ?: "") 
                    }
                }
                is Resource.Error -> {
                    _uiState.update { 
                        it.copy(isLoading = false, error = result.message) 
                    }
                }
                else -> {}
            }
        }
    }

    fun toggleReadingMode() {
        _uiState.update { it.copy(isReadingMode = !it.isReadingMode) }
    }
}
