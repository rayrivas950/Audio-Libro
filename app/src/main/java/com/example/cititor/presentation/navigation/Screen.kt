package com.example.cititor.presentation.navigation

sealed class Screen(val route: String) {
    object LibraryScreen : Screen("library_screen")
}
