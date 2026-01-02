package com.example.cititor.presentation.reader

import android.util.Log
import androidx.compose.runtime.State

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.cititor.core.tts.TextToSpeechManager
import com.example.cititor.data.worker.BookProcessingWorker
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
    val isProcessing: Boolean = false,
    val processingError: String? = null, // To show worker errors
    val highlightedTextRange: IntRange? = null,
    val dramatism: Float = 1.0f,
    val currentVoiceModel: String = "Miro High (ES)"
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
        val book = state.value.book
        if (book != null) {
            textToSpeechManager.speak(
                bookId = book.id,
                segments = state.value.pageSegments,
                category = book.category,
                pageIndex = state.value.currentPage
            )
        }
    }

    fun updateDramatism(value: Float) {
        _state.value = state.value.copy(dramatism = value)
        textToSpeechManager.setMasterDramatism(value)
    }

    fun setVoiceModel(name: String) {
        val config = when (name) {
            "Miro High (ES)" -> com.example.cititor.core.tts.piper.PiperModelConfig(
                assetDir = "piper/vits-piper-es_ES-miro-high",
                modelName = "es_ES-miro-high.onnx",
                configName = "es_ES-miro-high.onnx.json"
            )
            else -> com.example.cititor.core.tts.piper.PiperModelConfig(
                assetDir = "piper/vits-piper-es_ES-miro-high",
                modelName = "es_ES-miro-high.onnx",
                configName = "es_ES-miro-high.onnx.json"
            )
        }
        _state.value = _state.value.copy(currentVoiceModel = name)
        textToSpeechManager.switchModel(config)
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

                if (workInfo.state == WorkInfo.State.FAILED) {
                    val errorMessage = workInfo.outputData.getString(BookProcessingWorker.KEY_ERROR_MSG)
                    _state.value = state.value.copy(processingError = "Processing failed: $errorMessage")
                }

                if (isFinished) {
                    loadPageContent()
                }
            }.launchIn(viewModelScope)
    }

    private fun loadPageContent() {
        viewModelScope.launch {
            if (state.value.isProcessing) {
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

                if (segments != null && segments.isNotEmpty()) {
                    val displayText = segments.joinToString(separator = "") { it.text }
                    Log.d("ReaderViewModel", "Page $page loaded. Length: ${displayText.length}, Newlines: ${displayText.count { it == '\n' }}")
                    _state.value = state.value.copy(
                        pageSegments = segments,
                        pageDisplayText = displayText,
                        isLoading = false,
                        processingError = null // Clear previous errors
                    )
                } else {
                    _state.value = state.value.copy(
                        pageSegments = emptyList(),
                        pageDisplayText = if (state.value.processingError == null) "Page content not available." else "",
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
