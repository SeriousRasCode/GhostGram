package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.MainGhostScreen
import com.example.ui.theme.GhostGramTheme
import com.example.ui.viewmodel.GhostViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: GhostViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
            
            setContent {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                
                // Use user preference if set, otherwise follow system
                val isDarkMode = uiState.config.darkMode ?: androidx.compose.foundation.isSystemInDarkTheme()
                
                GhostGramTheme(darkTheme = isDarkMode) {
                    // Request notification permission for Android 13+ when UI is ready
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
                        }
                    }

                    MainGhostScreen(
                        viewModel = viewModel,
                        uiState = uiState
                    )
                }
            }
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "Fatal onCreate failure: ${e.message}")
        }
    }
}
