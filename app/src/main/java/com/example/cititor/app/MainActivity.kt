package com.example.cititor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.cititor.presentation.library.LibraryScreen
import com.example.cititor.presentation.navigation.Screen
import com.example.cititor.presentation.reader.ReaderScreen
import com.example.cititor.ui.theme.CititorTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CititorTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = Screen.LibraryScreen.route
                ) {
                    composable(route = Screen.LibraryScreen.route) {
                        LibraryScreen(navController = navController)
                    }
                    composable(
                        route = Screen.ReaderScreen.route,
                        arguments = listOf(navArgument("bookId") { type = NavType.LongType })
                    ) {
                        ReaderScreen()
                    }
                }
            }
        }
    }
}
