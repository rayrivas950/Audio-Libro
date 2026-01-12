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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.serialization.json.Json

data class VirtualPageInfo(
    val segments: List<TextSegment>,
    val chapterIndex: Int
)

data class ReaderState(
    val book: Book? = null,
    // windowChapters: Maps physical chapter index to its segments
    val windowChapters: Map<Int, List<TextSegment>> = emptyMap(),
    // virtualPages: Sliced content across all loaded chapters
    val virtualPages: List<VirtualPageInfo> = emptyList(),
    val pageDisplayText: String = "",
    val currentPage: Int = 0, // Current Chapter (Physical Page)
    val currentVirtualPage: Int = 0, // Global Index within virtualPages
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false,
    val processingError: String? = null,
    val highlightedTextRange: IntRange? = null,
    val dramatism: Float = 1.0f,
    val currentVoiceModel: String = "Miro High (ES)",
    val bookTheme: com.example.cititor.domain.theme.BookTheme = com.example.cititor.domain.theme.BookTheme.DEFAULT,
    val navigationMode: com.example.cititor.domain.model.ReaderNavigationMode = com.example.cititor.domain.model.ReaderNavigationMode.PAGINATED
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

    companion object {
        private const val BUFFER_SIZE = 10
        private const val REFILL_THRESHOLD = 7
    }

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state

    private var lastRefilledChapter = -1

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
        val pageInfo = state.value.virtualPages.getOrNull(state.value.currentVirtualPage) ?: return
        val book = state.value.book ?: return
        
        textToSpeechManager.speak(
            bookId = book.id,
            segments = pageInfo.segments,
            category = book.category,
            pageIndex = pageInfo.chapterIndex
        )
    }

    fun onVirtualPageChanged(index: Int) {
        val infos = state.value.virtualPages
        if (index < 0 || index >= infos.size) return
        
        val pageInfo = infos[index]
        val oldChapterIndex = state.value.currentPage
        
        _state.value = state.value.copy(currentVirtualPage = index)
        
        if (pageInfo.chapterIndex != oldChapterIndex) {
            Log.d("ReaderViewModel", "Chapter boundary crossed: $oldChapterIndex -> ${pageInfo.chapterIndex}")
            _state.value = state.value.copy(currentPage = pageInfo.chapterIndex)
            saveProgress()
            
            // Refill Check (Tank Logic)
            val windowKeys = state.value.windowChapters.keys.sorted()
            val maxChapterLoaded = windowKeys.lastOrNull() ?: pageInfo.chapterIndex
            
            if (pageInfo.chapterIndex >= lastRefilledChapter + (BUFFER_SIZE * 0.7).toInt()) {
                Log.d("ReaderViewModel", "Triggering buffer refill at chapter ${pageInfo.chapterIndex}")
                loadWindowContent(startFrom = maxChapterLoaded + 1)
            }
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

    fun setVirtualPages(pages: List<VirtualPageInfo>) {
        _state.value = state.value.copy(virtualPages = pages)
    }

    private fun loadWindowContent(startFrom: Int? = null) {
        viewModelScope.launch(Dispatchers.Default) {
            val book = state.value.book ?: return@launch
            val current = startFrom ?: state.value.currentPage
            
            // If it's the initial load, we might want current and current + 10
            // If it's a refill, we want [startFrom, startFrom + 10]
            val targets = (current until (current + BUFFER_SIZE)).filter { it >= 0 && it < book.totalPages }
            
            if (targets.isEmpty()) return@launch
            
            val newWindow = state.value.windowChapters.toMutableMap()
            var modified = false
            
            // Only show loader if we have ABSOLUTELY NOTHING for the current page
            if (!newWindow.containsKey(state.value.currentPage)) {
                withContext(Dispatchers.Main) {
                    _state.value = state.value.copy(isLoading = true)
                }
            }

            for (target in targets) {
                if (!newWindow.containsKey(target)) {
                    val segments = getBookPageUseCase(book.id, target)
                    if (segments != null) {
                        newWindow[target] = segments
                        modified = true
                    }
                }
            }
            
            if (modified) {
                lastRefilledChapter = current
                withContext(Dispatchers.Main) {
                    _state.value = state.value.copy(
                        windowChapters = newWindow,
                        isLoading = false,
                        processingError = null
                    )
                }
            } else {
                withContext(Dispatchers.Main) {
                    _state.value = state.value.copy(isLoading = false)
                }
            }
        }
    }

    private fun loadBook(bookId: Long) {
        getBookUseCase(bookId).onEach { book ->
            if (book != null) {
                val theme = book.themeJson?.let {
                    try {
                        Json.decodeFromString<com.example.cititor.domain.theme.BookTheme>(it)
                    } catch (e: Exception) {
                        Log.e("ReaderViewModel", "Error deserializing book theme", e)
                        com.example.cititor.domain.theme.BookTheme.DEFAULT
                    }
                } ?: com.example.cititor.domain.theme.BookTheme.DEFAULT
                
                _state.value = state.value.copy(
                    book = book, 
                    currentPage = book.currentPage,
                    bookTheme = theme
                )
                observeWorkStatus(book.processingWorkId)
                loadWindowContent()
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
                    loadWindowContent()
                }
            }.launchIn(viewModelScope)
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
