package com.example.cititor.presentation.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cititor.core.tts.TextToSpeechManager
import com.example.cititor.domain.model.Book
import com.example.cititor.domain.use_case.GetBookPageUseCase
import com.example.cititor.domain.use_case.GetBookUseCase
import com.example.cititor.domain.use_case.UpdateBookProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderState(
    val book: Book? = null,
    val pageContent: String = "",
    val currentPage: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val getBookUseCase: GetBookUseCase,
    private val getBookPageUseCase: GetBookPageUseCase,
    private val updateBookProgressUseCase: UpdateBookProgressUseCase,
    private val textToSpeechManager: TextToSpeechManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state

    init {
        savedStateHandle.get<Long>("bookId")?.let {
            loadBook(it)
        }
    }

    fun startReading() {
        textToSpeechManager.speak(state.value.pageContent)
    }

    fun nextPage() {
        val book = state.value.book ?: return
        val currentPage = state.value.currentPage
        if (currentPage < book.totalPages - 1) {
            val newPage = currentPage + 1
            _state.value = state.value.copy(currentPage = newPage)
            loadPageContent()
            saveProgress()
        }
    }

    fun previousPage() {
        val book = state.value.book ?: return
        val currentPage = state.value.currentPage
        if (currentPage > 0) {
            val newPage = currentPage - 1
            _state.value = state.value.copy(currentPage = newPage)
            loadPageContent()
            saveProgress()
        }
    }

    private fun loadBook(bookId: Long) {
        getBookUseCase(bookId).onEach { book ->
            if (book != null) {
                _state.value = state.value.copy(
                    book = book,
                    currentPage = book.currentPage,
                    isLoading = false
                )
                loadPageContent()
            }
        }.launchIn(viewModelScope)
    }

    private fun loadPageContent() {
        viewModelScope.launch {
            val book = state.value.book
            if (book != null) {
                _state.value = state.value.copy(isLoading = true)
                val content = getBookPageUseCase(book.filePath, state.value.currentPage)
                _state.value = state.value.copy(pageContent = content ?: "Error loading page", isLoading = false)
            }
        }
    }

    private fun saveProgress() {
        viewModelScope.launch {
            val book = state.value.book
            if (book != null) {
                updateBookProgressUseCase(book, state.value.currentPage)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        textToSpeechManager.shutdown()
    }
}
