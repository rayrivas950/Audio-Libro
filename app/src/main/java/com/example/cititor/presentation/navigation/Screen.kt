package com.example.cititor.presentation.navigation

sealed class Screen(val route: String) {
    object LibraryScreen : Screen("library_screen")

    object ReaderScreen : Screen("reader_screen/{bookId}") {
        fun withArgs(bookId: Long): String {
            return "reader_screen/$bookId"
        }
    }
}