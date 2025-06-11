package com.example.languageapp.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.languageapp.data.AppDatabase
import com.example.languageapp.data.ChatRepository
import com.example.languageapp.data.UserSettingsRepository
import com.example.languageapp.data.model.ChatConversationEntity
import com.example.languageapp.data.model.ChatMessageEntity
import com.example.languageapp.llm.LlmServiceState
import com.example.languageapp.llm.MediaPipeLlmService
import com.example.languageapp.llm.ModelDownloader
import com.example.languageapp.ui.components.ChatMessageBubble
import com.example.languageapp.ui.components.ModelDownloadDialog
import com.example.languageapp.ui.components.ModelDownloadDialogState
import com.example.languageapp.ui.theme.LanguageAppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

@Composable
fun ChatScreen(
    chatId: String?,
    languageCode: String? = null,
    topicId: String? = null,
    chatRepository: ChatRepository,
    userSettingsRepository: UserSettingsRepository,
    llmService: MediaPipeLlmService // Added
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var currentChatId by remember { mutableStateOf(chatId) }
    var conversationTitle by remember { mutableStateOf("Chat") }

    val messagesFlow: Flow<List<ChatMessageEntity>> = remember(currentChatId) {
        currentChatId?.let {
            chatRepository.getMessagesForConversation(it)
        } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }
    val messages by messagesFlow.collectAsState(initial = emptyList())
    var inputText by remember { mutableStateOf("") }

    // LLM Service State & Dialog Management
    val llmState by llmService.serviceState.collectAsStateWithLifecycle()
    var downloadDialogController by remember { mutableStateOf(ModelDownloadDialogState(showDialog = false)) }

    LaunchedEffect(llmService) {
        // Initialize the service if it's idle or in an error state from a previous attempt with a different model.
        // Or if we want to ensure a specific model for this chat screen is loaded.
        // For now, let's assume one global llmService instance, initialize if Idle.
        if (llmState is LlmServiceState.Idle || llmState is LlmServiceState.Error) {
             Log.d("ChatScreen", "Attempting to initialize LLM Service from ChatScreen.")
             launch { // Use a new coroutine for suspend function
                llmService.initialize()
            }
        }
    }

    LaunchedEffect(llmState) {
        Log.d("ChatScreen", "LLM State changed: $llmState")
        when (val currentLlmState = llmState) {
            is LlmServiceState.Downloading -> {
                downloadDialogController = ModelDownloadDialogState(
                    showDialog = true,
                    modelInfo = currentLlmState.model,
                    progress = currentLlmState.progress,
                    isComplete = false,
                    errorMessage = null
                )
            }
            is LlmServiceState.Error -> {
                downloadDialogController = ModelDownloadDialogState(
                    showDialog = true,
                    modelInfo = currentLlmState.modelBeingProcessed,
                    progress = downloadDialogController.progress, // Keep last progress
                    isComplete = false,
                    errorMessage = currentLlmState.message
                )
            }
            LlmServiceState.Ready -> {
                if (downloadDialogController.showDialog && !downloadDialogController.isComplete && downloadDialogController.errorMessage == null) {
                    // If dialog was showing for download, mark as complete
                     downloadDialogController = downloadDialogController.copy(isComplete = true, progress = 100f)
                } else if (downloadDialogController.errorMessage != null) {
                    // If an error was shown, and then it became ready (e.g. retry outside dialog), hide dialog.
                    downloadDialogController = downloadDialogController.copy(showDialog = false)
                }
            }
            LlmServiceState.Idle, LlmServiceState.Initializing -> {
                // Can show a generic loading or just wait for download/ready state
                 if (downloadDialogController.showDialog && !downloadDialogController.isComplete) {
                    // If dialog is already showing (e.g. for a download that was cancelled and service reset)
                    // update it to reflect initializing or hide it.
                    // For now, let's assume it might briefly show "Initializing" if a download was cancelled.
                    // Or simply:
                    // downloadDialogController = downloadDialogController.copy(showDialog = false)
                 }
            }
        }
    }

    if (downloadDialogController.showDialog) {
        ModelDownloadDialog(
            state = downloadDialogController,
            onDismissRequest = { downloadDialogController = downloadDialogController.copy(showDialog = false) },
            onRetry = { modelInfo ->
                Log.d("ChatScreen", "Retry download for model: ${modelInfo?.modelName}")
                downloadDialogController = downloadDialogController.copy(errorMessage = null, progress = 0f) // Reset error and progress
                coroutineScope.launch { llmService.initialize() } // Re-trigger initialization
            }
        )
    }


    LaunchedEffect(key1 = chatId, key2 = languageCode, key3 = topicId) {
        if (chatId != null) {
            currentChatId = chatId
            conversationTitle = "Chat with Buddy" // Placeholder
        } else if (languageCode != null && topicId != null) {
            val newConversationId = UUID.randomUUID().toString()
            currentChatId = newConversationId
            conversationTitle = "$languageCode: $topicId"
            val newConversation = ChatConversationEntity(
                id = newConversationId,
                targetLanguageCode = languageCode,
                topicId = topicId,
                lastMessage = null,
                lastMessageTimestamp = System.currentTimeMillis(),
                userProfileImageUrl = null,
                conversationTitle = conversationTitle
            )
            if (llmState is LlmServiceState.Ready) { // Only start if LLM is ready
                 Log.d("ChatScreen", "LLM Ready, starting new conversation via repository.")
                chatRepository.startNewConversation(newConversation)
            } else {
                Log.w("ChatScreen", "LLM not ready when trying to start new conversation. State: $llmState. User will need to send first message manually or wait.")
                // Optionally, save the conversation without the initial AI message,
                // or wait for LLM to be ready and then trigger.
                // For now, just log. The user can initiate by sending a message once LLM is ready.
                 chatDao.insertConversation(newConversation) // Insert basic conversation details
                 chatDao.updateConversationSummary(newConversation.id, "Conversation created. Waiting for AI.", System.currentTimeMillis())
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(conversationTitle) }, backgroundColor = MaterialTheme.colors.primary) },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    colors = TextFieldDefaults.textFieldColors(backgroundColor = MaterialTheme.colors.surface)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val currentId = currentChatId
                        if (inputText.isNotBlank() && currentId != null) {
                            val userMessage = ChatMessageEntity(
                                conversationId = currentId,
                                text = inputText,
                                timestamp = System.currentTimeMillis(),
                                isUserMessage = true
                            )
                            coroutineScope.launch {
                                chatRepository.sendMessage(currentId, userMessage)
                                inputText = ""
                            }
                        }
                    },
                    // Updated enabled logic
                    enabled = currentChatId != null && llmState is LlmServiceState.Ready
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Send message")
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 8.dp),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom)
        ) {
            items(messages.reversed()) { message ->
                ChatMessageBubble(
                    messageText = message.text,
                    isUserMessage = message.isUserMessage,
                    showSpeakerIcon = !message.isUserMessage,
                    onSpeakerIconClick = {
                        Log.d("ChatScreen", "Speaker icon clicked for message: ${message.text}")
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun ChatScreenPreview_ExistingChat() {
    val context = LocalContext.current
    val mockLlmService: MediaPipeLlmService = mock()
    whenever(mockLlmService.serviceState).thenReturn(MutableStateFlow(LlmServiceState.Ready))

    val dummyRepo = ChatRepository(
        AppDatabase.getInstance(context).chatDao(),
        mockLlmService // Pass the mocked LlmService
    )
    val dummyUserSettingsRepo = UserSettingsRepository(context)
    LanguageAppTheme {
        ChatScreen(
            chatId = "previewChatId",
            chatRepository = dummyRepo,
            userSettingsRepository = dummyUserSettingsRepo,
            llmService = mockLlmService // Pass the mocked LlmService
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, name = "New Chat From Topic Preview")
@Composable
fun ChatScreenPreview_NewChatFromTopic() {
    val context = LocalContext.current
    val mockLlmService: MediaPipeLlmService = mock()
    whenever(mockLlmService.serviceState).thenReturn(MutableStateFlow(LlmServiceState.Ready))
    // Simulate a model that requires download for this preview if needed for dialog testing
    // whenever(mockLlmService.serviceState).thenReturn(MutableStateFlow(LlmServiceState.Downloading(ModelManager.DEFAULT_MODEL, 50f)))


    val dummyRepo = ChatRepository(
        AppDatabase.getInstance(context).chatDao(),
        mockLlmService
    )
    val dummyUserSettingsRepo = UserSettingsRepository(context)
    LanguageAppTheme {
        ChatScreen(
            chatId = null,
            languageCode = "es",
            topicId = "greetings",
            chatRepository = dummyRepo,
            userSettingsRepository = dummyUserSettingsRepo,
            llmService = mockLlmService
        )
    }
}

// Dummy DAO for previewing ChatScreen when LLM is not ready
private val chatDao: ChatDao = mock()
