package com.example.cititor.presentation.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cititor.domain.model.Book
import com.example.cititor.domain.use_case.AddBookUseCase
import com.example.cititor.domain.use_case.GetBooksUseCase
import com.github.mertakdut.Reader
import com.github.mertakdut.exception.OutOfPagesException
import com.github.mertakdut.exception.ReadingException
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
import java.io.File
import java.io.FileOutputStream
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
            // Persist permission for the URI
            val contentResolver = application.contentResolver
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)

            val mimeType = contentResolver.getType(uri)

            when (mimeType) {
                "application/pdf" -> parsePdf(uri)
                "application/epub+zip" -> parseEpub(uri)
                else -> { // Handle unsupported file types
                }
            }
        }
    }

    private suspend fun parsePdf(uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                application.contentResolver.openInputStream(uri)?.use { inputStream ->
                    PDDocument.load(inputStream).use { document ->
                        val newBook = Book(
                            id = 0, // Room will auto-generate
                            title = document.documentInformation.title ?: "Untitled PDF",
                            author = document.documentInformation.author,
                            filePath = uri.toString(),
                            coverPath = null, // Future feature
                            currentPage = 0,
                            totalPages = document.numberOfPages,
                            lastReadTimestamp = System.currentTimeMillis(),
                            processingWorkId = null
                        )
                        addBookUseCase(newBook)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun parseEpub(uri: Uri) {
        withContext(Dispatchers.IO) {
            var tempFile: File? = null
            try {
                tempFile = File.createTempFile("temp_epub", ".epub", application.cacheDir)
                application.contentResolver.openInputStream(uri)?.use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                val reader = Reader()
                reader.setFullContent(tempFile.absolutePath)

                val title = reader.infoPackage.metadata.title ?: "Untitled EPUB"
                val author = reader.infoPackage.metadata.creator ?: "Unknown Author"
                
                var sectionCount = 0
                while (true) {
                    try {
                        reader.readSection(sectionCount)
                        sectionCount++
                    } catch (e: OutOfPagesException) {
                        // We have reached the end of the book. This is the exit condition.
                        break
                    }
                }

                val newBook = Book(
                    id = 0, // Room will auto-generate
                    title = title,
                    author = author,
                    filePath = uri.toString(),
                    coverPath = null, // Future feature
                    currentPage = 0,
                    totalPages = sectionCount,
                    lastReadTimestamp = System.currentTimeMillis(),
                    processingWorkId = null
                )
                addBookUseCase(newBook)

            } catch (e: ReadingException) {
                e.printStackTrace()
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                tempFile?.delete()
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
