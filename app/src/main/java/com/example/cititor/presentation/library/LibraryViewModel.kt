package com.example.cititor.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cititor.domain.model.Book
import com.example.cititor.domain.use_case.GetBooksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    private val getBooksUseCase: GetBooksUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state

    init {
        getBooks()
    }

    private fun getBooks() {
        getBooksUseCase().onEach { books ->
            _state.value = state.value.copy(
                books = books
            )
        }.launchIn(viewModelScope)
    }
}
