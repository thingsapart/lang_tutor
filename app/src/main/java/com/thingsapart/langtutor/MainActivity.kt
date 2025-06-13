package com.thingsapart.langtutor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.ui.Modifier
import com.thingsapart.langtutor.ui.theme.LangTutorAppTheme
import com.thingsapart.langtutor.data.AppDatabase
import com.thingsapart.langtutor.data.UserSettingsRepository
//import com.thingsapart.langtutor.llm.LiteRtLlmService
import com.thingsapart.langtutor.llm.ModelDownloader
import com.thingsapart.langtutor.ui.AppNavigator
import com.thingsapart.langtutor.llm.LlmModelConfig
import com.thingsapart.langtutor.llm.LlmService
import com.thingsapart.langtutor.llm.MediaPipeLlmService
import com.thingsapart.langtutor.llm.ModelManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private lateinit var userSettingsRepository: UserSettingsRepository
    private lateinit var chatRepository: com.thingsapart.langtutor.data.ChatRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userSettingsRepository = UserSettingsRepository(applicationContext)
        val database = AppDatabase.getInstance(applicationContext)
        val modelDownloader = ModelDownloader() // Added

        // Initialize LlmService dynamically
        val llmService: LlmService = runBlocking { // Use runBlocking for simplicity here
            //val selectedModelId = userSettingsRepository.getSelectedModel().first()
            val selectedModelId = ModelManager.DEFAULT_MODEL.internalModelId
            val modelConfig = ModelManager.getAllModels().find { it.internalModelId == selectedModelId } ?: ModelManager.DEFAULT_MODEL

            //if (modelConfig.llmBackend == com.thingsapart.langtutor.llm.LlmBackend.MEDIA_PIPE) {
                MediaPipeLlmService(applicationContext, modelConfig, modelDownloader)
            //} else {
                //LiteRtLlmService(applicationContext, modelConfig, modelDownloader)
            //}
        }

        // Provide LlmService to ChatRepository
        chatRepository = com.thingsapart.langtutor.data.ChatRepository(
            database.chatDao(),
            llmService
        ) // Modified

        setContent {
            LangTutorAppTheme {
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
