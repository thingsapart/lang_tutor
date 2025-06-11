package com.example.languageapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.languageapp.data.AppDatabase
import com.example.languageapp.data.ChatRepository
import com.example.languageapp.data.UserSettingsRepository
import com.example.languageapp.llm.MediaPipeLlmService
import com.example.languageapp.llm.ModelDownloader // Added import
import com.example.languageapp.ui.AppNavigator // Corrected to AppNavigator based on file
import com.example.languageapp.ui.theme.LanguageAppTheme

class MainActivity : ComponentActivity() {

    private lateinit var userSettingsRepository: UserSettingsRepository
    private lateinit var chatRepository: ChatRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userSettingsRepository = UserSettingsRepository(applicationContext)
        val database = AppDatabase.getInstance(applicationContext)
        val modelDownloader = ModelDownloader() // Added
        // Initialize LlmService
        val llmService = MediaPipeLlmService(applicationContext, modelDownloader = modelDownloader) // Modified
        // Provide LlmService to ChatRepository
        chatRepository = ChatRepository(database.chatDao(), llmService) // Modified

        setContent {
            LanguageAppTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    AppNavigator(
                        userSettingsRepository = userSettingsRepository,
                        chatRepository = chatRepository,
                        llmService = llmService // Added llmService parameter
                    )
                }
            }
        }
    }
}

// Preview for AppNavigator can be added if needed, or individual screen previews are used.
// For now, we rely on individual screen previews.
