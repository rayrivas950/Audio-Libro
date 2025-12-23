package com.example.cititor.presentation.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cititor.core.tts.TextToSpeechManager
import com.example.cititor.domain.model.Book
import com.example.cititor.domain.model.TextSegment
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
    val pageSegments: List<TextSegment> = emptyList(),
    val pageDisplayText: String = "", // For UI display
    val currentPage: Int = 0,
    val isLoading: Boolean = true,
    val highlightedTextRange: IntRange? = null
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val getBookUseCase: GetBookUseCase,
    private val getBookPageUseCase: GetBookPageUseCase,
    private val updateBookProgressUseCase: UpdateBookProgressUseCase,
    private val textToSpeechManager: TextToSpeechManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state

    init {
        savedStateHandle.get<Long>("bookId")?.let {
            loadBook(it)
        }

        textToSpeechManager.currentSpokenWord.onEach { range ->
            _state.value = state.value.copy(highlightedTextRange = range)
        }.launchIn(viewModelScope)
    }

    fun startReading() {
        // Pass the structured data to the TTS manager
        textToSpeechManager.speak(state.value.pageSegments)
    }

    fun nextPage() {
        val book = state.value.book ?: return
        val currentPage = state.value.currentPage
        if (currentPage < book.totalPages - 1) {
            loadPage(currentPage + 1)
        }
    }

    fun previousPage() {
        val currentPage = state.value.currentPage
        if (currentPage > 0) {
            loadPage(currentPage - 1)
        }
    }

    private fun loadPage(pageNumber: Int) {
        _state.value = state.value.copy(currentPage = pageNumber)
        loadPageContent()
        saveProgress()
    }

    private fun loadBook(bookId: Long) {
        getBookUseCase(bookId).onEach { book ->
            if (book != null) {
                _state.value = state.value.copy(
                    book = book,
                    currentPage = book.currentPage
                )
                loadPageContent()
            } else {
                _state.value = state.value.copy(isLoading = false)
            }
        }.launchIn(viewModelScope)
    }

    private fun loadPageContent() {
        viewModelScope.launch {
            val book = state.value.book
            val page = state.value.currentPage
            if (book != null) {
                _state.value = state.value.copy(isLoading = true)
                val segments = getBookPageUseCase(book.id, page)

                if (segments != null) {
                    val displayText = segments.joinToString(separator = " ") { it.text }
                    _state.value = state.value.copy(
                        pageSegments = segments,
                        pageDisplayText = displayText,
                        isLoading = false
                    )
                } else {
                    _state.value = state.value.copy(
                        pageSegments = emptyList(),
                        pageDisplayText = "Page content not yet processed. Please try again later.",
                        isLoading = false
                    )
                }
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
