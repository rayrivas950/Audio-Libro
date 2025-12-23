package com.example.cititor.presentation.reader

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.cititor.core.tts.TextToSpeechManager
import com.example.cititor.domain.model.Book
import com.example.cititor.domain.model.TextSegment
import com.example.cititor.domain.use_case.GetBookPageUseCase
import com.example.cititor.domain.use_case.GetBookUseCase
import com.example.cititor.domain.use_case.UpdateBookProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ReaderState(
    val book: Book? = null,
    val pageSegments: List<TextSegment> = emptyList(),
    val pageDisplayText: String = "",
    val currentPage: Int = 0,
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false, // To track the background worker
    val highlightedTextRange: IntRange? = null
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getBookUseCase: GetBookUseCase,
    private val getBookPageUseCase: GetBookPageUseCase,
    private val updateBookProgressUseCase: UpdateBookProgressUseCase,
    private val textToSpeechManager: TextToSpeechManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state

    private var workInfoJob: Job? = null
    private val workManager = WorkManager.getInstance(context)

    init {
        savedStateHandle.get<Long>("bookId")?.let {
            loadBook(it)
        }

        textToSpeechManager.currentSpokenWord.onEach { range ->
            _state.value = state.value.copy(highlightedTextRange = range)
        }.launchIn(viewModelScope)
    }

    fun startReading() {
        textToSpeechManager.speak(state.value.pageSegments)
    }

    fun nextPage() {
        val book = state.value.book ?: return
        if (state.value.currentPage < book.totalPages - 1) {
            loadPage(state.value.currentPage + 1)
        }
    }

    fun previousPage() {
        if (state.value.currentPage > 0) {
            loadPage(state.value.currentPage - 1)
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
                _state.value = state.value.copy(book = book, currentPage = book.currentPage)
                observeWorkStatus(book.processingWorkId)
                loadPageContent()
            } else {
                _state.value = state.value.copy(isLoading = false)
            }
        }.launchIn(viewModelScope)
    }

    private fun observeWorkStatus(workId: String?) {
        if (workId == null) {
            _state.value = state.value.copy(isProcessing = false)
            return
        }

        workInfoJob?.cancel()
        workInfoJob = workManager.getWorkInfoByIdFlow(UUID.fromString(workId))
            .onEach { workInfo ->
                val isFinished = workInfo.state.isFinished
                _state.value = state.value.copy(isProcessing = !isFinished)
                if (isFinished) {
                    // Once processing is done, reload the content
                    loadPageContent()
                }
            }.launchIn(viewModelScope)
    }

    private fun loadPageContent() {
        viewModelScope.launch {
            if (state.value.isProcessing) {
                // Don't try to load content while the book is being processed
                _state.value = state.value.copy(
                    isLoading = false,
                    pageDisplayText = "Analysing book for the first time, please wait..."
                )
                return@launch
            }

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
                        pageDisplayText = "Page content not available.",
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
