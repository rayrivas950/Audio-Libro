package com.example.audiobook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.audiobook.feature_library.presentation.LibraryScreen
import com.example.audiobook.feature_reader.presentation.ReaderScreen
import com.example.audiobook.ui.theme.AudiobookTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AudiobookAppTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    NavHost(navController = navController, startDestination = "library") {
                        
                        // Route 1: Library (Home)
                        composable("library") {
                            LibraryScreen(
                                onBookClick = { bookId ->
                                    navController.navigate("reader/$bookId")
                                }
                            )
                        }

                        // Route 2: Reader (Detail)
                        composable(
                            route = "reader/{bookId}",
                            arguments = listOf(navArgument("bookId") { type = NavType.LongType })
                        ) {
                            ReaderScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

