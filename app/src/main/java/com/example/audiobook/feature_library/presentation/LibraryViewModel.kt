```
package com.example.audiobook.feature_library.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.core.common.Resource
import com.example.audiobook.core.database.entity.BookEntity
import com.example.audiobook.feature_library.domain.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// 1. Define the "State" of our screen.
// This is what the UI will "Draw".
data class LibraryUiState(
    val books: List<BookEntity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val importedBookTitle: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository
) : ViewModel() {

    // 2. MutableStateFlow is like a "Live Data Stream".
    // We keep it private so only this ViewModel can change it.
    private val _uiState = MutableStateFlow(LibraryUiState())
    


    // 3. We expose a read-only version to the UI.
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    // 4. The list of books, reacting to search query
    @OptIn(ExperimentalCoroutinesApi::class)
    val books: StateFlow<List<BookEntity>> = _uiState
        .flatMapLatest { state ->
            if (state.searchQuery.isBlank()) {
                repository.getBooks()
            } else {
                repository.searchBooks(state.searchQuery)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSearchActiveChange(isActive: Boolean) {
        _uiState.update { it.copy(isSearchActive = isActive) }
        if (!isActive) {
            onSearchQueryChange("") // Clear query when closing search
        }
    }

    fun onFilePicked(uri: Uri) {
        // Launch a coroutine (a lightweight thread)
        viewModelScope.launch {
            // Step A: Show Loading
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Step B: Call the Repository (The Waiter)
            when (val result = repository.processNewBook(uri)) {
                is Resource.Success -> {
                    // Step C1: Success! Show the book title
                    _uiState.update { 
                        it.copy(isLoading = false, importedBookTitle = result.data) 
                    }
                }
                is Resource.Error -> {
                    // Step C2: Error! Show the message
                    _uiState.update { 
                        it.copy(isLoading = false, error = result.message) 
                    }
                }
                else -> {} // Loading handled above
            }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
