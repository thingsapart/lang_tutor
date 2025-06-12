package com.thingsapart.langtutor.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // Ensure this import is present
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // Ensure this is imported
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thingsapart.langtutor.data.AppDatabase
import com.thingsapart.langtutor.data.UserSettingsRepository
// import com.thingsapart.langtutor.data.dao.ChatDao // Not directly used in this file after repository pattern
import com.thingsapart.langtutor.data.model.ChatConversationEntity
import com.thingsapart.langtutor.data.model.ChatMessageEntity
import com.thingsapart.langtutor.llm.LlmService // Interface
import com.thingsapart.langtutor.llm.LlmServiceState
import com.thingsapart.langtutor.llm.ModelManager // Needed for FakeLlmService example state
// import com.thingsapart.langtutor.llm.MediaPipeLlmService // No longer directly needed if AppNav passes LlmService
import com.thingsapart.langtutor.ui.components.ChatMessageBubble
import com.thingsapart.langtutor.ui.components.MetallicPanelGradientBackground // New import
import com.thingsapart.langtutor.ui.components.ModelDownloadDialog
import com.thingsapart.langtutor.ui.components.ModelDownloadDialogState
import com.thingsapart.langtutor.ui.theme.AiBubbleColor
import com.thingsapart.langtutor.ui.theme.SomeDarkColorForText
import com.thingsapart.langtutor.ui.theme.UserBubbleColor
import kotlinx.coroutines.delay
import com.thingsapart.langtutor.ui.theme.LangTutorAppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.UUID

class FakeLlmService(initialState: LlmServiceState = LlmServiceState.Ready) : LlmService {
    override val serviceState = MutableStateFlow(initialState)

    override suspend fun initialize() {
        serviceState.value = LlmServiceState.Initializing
        delay(100) // Simulate some work
        serviceState.value = LlmServiceState.Ready
        Log.d("FakeLlmService", "Fake initialized")
    }

    override fun generateResponse(prompt: String, conversationId: String, targetLanguage: String): Flow<String> {
        Log.d("FakeLlmService", "Fake generateResponse called with prompt: $prompt")
        return flow {
            emit("This is a fake response to: ")
            delay(50)
            emit(prompt.take(50) + "...")
        }
    }

    override suspend fun getInitialGreeting(topic: String, targetLanguage: String): String {
        Log.d("FakeLlmService", "Fake getInitialGreeting for topic: $topic")
        return "Hello! Let's talk about $topic in $targetLanguage. (Fake)"
    }

    override fun resetSession() { // Changed signature
        serviceState.value = LlmServiceState.Idle // Corrected: Use public serviceState
        Log.d("FakeLlmService", "Fake session reset called (now synchronous)")
        // Optionally, simulate re-initialization if needed for preview state
        // _serviceState.value = LlmServiceState.Initializing
        // _serviceState.value = LlmServiceState.Ready
    }

    override fun close() {
        serviceState.value = LlmServiceState.Idle
        Log.d("FakeLlmService", "Fake closed")
    }
}

@Composable
fun ChatScreen(
    chatId: String?,
    languageCode: String? = null,
    topicId: String? = null,
    chatRepository: com.thingsapart.langtutor.data.ChatRepository,
    userSettingsRepository: UserSettingsRepository,
    llmService: LlmService // Changed to LlmService interface
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var currentChatId by remember { mutableStateOf(chatId) }
    var conversationTitle by remember { mutableStateOf("Chat") }

    // Define contrasting colors (these could also come from a Theme extension)
    val textColorOnGradient = SomeDarkColorForText // Use a predefined dark color for high contrast
    val userBubbleBackgroundColor = UserBubbleColor
    val aiBubbleBackgroundColor = AiBubbleColor
    val textOnUserBubbleColor = SomeDarkColorForText
    val textOnAiBubbleColor = SomeDarkColorForText

    val messagesFlow: Flow<List<ChatMessageEntity>> = remember(currentChatId) {
        currentChatId?.let {
            chatRepository.getMessagesForConversation(it)
        } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }
    val messages by messagesFlow.collectAsState(initial = emptyList())
    var inputText by remember { mutableStateOf("") }

    val llmState by llmService.serviceState.collectAsStateWithLifecycle()
    var downloadDialogController by remember { mutableStateOf(ModelDownloadDialogState(showDialog = false)) }

    LaunchedEffect(llmService) {
        if (llmState is LlmServiceState.Idle || llmState is LlmServiceState.Error) {
            Log.d("ChatScreen", "Attempting to initialize LLM Service from ChatScreen.")
            launch {
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
                    progress = downloadDialogController.progress,
                    isComplete = false,
                    errorMessage = currentLlmState.message
                )
            }

            LlmServiceState.Ready -> {
                if (downloadDialogController.showDialog && !downloadDialogController.isComplete && downloadDialogController.errorMessage == null) {
                    downloadDialogController =
                        downloadDialogController.copy(isComplete = true, progress = 100f)
                } else if (downloadDialogController.errorMessage != null) {
                    downloadDialogController = downloadDialogController.copy(showDialog = false)
                }
            }

            LlmServiceState.Idle, LlmServiceState.Initializing -> {
                // Handled by other states or initial view
            }
        }
    }

    if (downloadDialogController.showDialog) {
        ModelDownloadDialog(
            state = downloadDialogController,
            onDismissRequest = {
                downloadDialogController = downloadDialogController.copy(showDialog = false)
            },
            onRetry = { modelInfo ->
                Log.d("ChatScreen", "Retry download for model: ${modelInfo?.modelName}")
                downloadDialogController =
                    downloadDialogController.copy(errorMessage = null, progress = 0f)
                coroutineScope.launch { llmService.initialize() }
            }
        )
    }

    LaunchedEffect(key1 = chatId, key2 = languageCode, key3 = topicId) {
        if (chatId != null) {
            currentChatId = chatId
            // Fetch title if needed
            chatRepository.getConversationById(chatId)
                .collect { conversation -> // Assuming getConversationById exists in repo and returns Flow<ChatConversationEntity?>
                    conversationTitle = conversation?.conversationTitle ?: "Chat"
                }
        } else if (languageCode != null && topicId != null) {
            // Try to find existing conversation by language and topic
            val existingConversation =
                chatRepository.getConversationByLanguageAndTopic(languageCode, topicId)
                    .firstOrNull() // Use firstOrNull() for one-time check

            if (existingConversation != null) {
                currentChatId = existingConversation.id
                conversationTitle = existingConversation.conversationTitle
                Log.d("ChatScreen", "Found existing conversation: ${existingConversation.id}")
            } else {
                // No existing conversation, create a new one
                val newConversationId = UUID.randomUUID().toString()
                currentChatId = newConversationId
                // Generate a more descriptive title, perhaps based on actual topic name later if available
                // For now, topicId is good.
                conversationTitle = "Chat: $languageCode - $topicId"
                val newConversation = ChatConversationEntity(
                    id = newConversationId,
                    targetLanguageCode = languageCode,
                    topicId = topicId,
                    lastMessage = null, // Will be set by initial greeting
                    lastMessageTimestamp = System.currentTimeMillis(), // Initial timestamp
                    userProfileImageUrl = null, // Placeholder
                    conversationTitle = conversationTitle
                )
                Log.d(
                    "ChatScreen",
                    "Creating new conversation: $newConversationId for $languageCode, $topicId"
                )
                // The startNewConversation method in ChatRepository already handles LLM interaction
                // and initial message.
                // It's important that startNewConversation is robust enough or that LLM readiness is handled.
                // The existing code already has some LLM state checks for sending messages.
                // Consider if LLM must be ready before even attempting to create in DB.
                // For now, assuming repository handles this.
                chatRepository.startNewConversation(newConversation)
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    MetallicPanelGradientBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            // Make Scaffold background transparent to see the gradient
            backgroundColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            conversationTitle,
                            color = textColorOnGradient
                        )
                    }, // Ensure title is readable
                    // Consider making TopAppBar background transparent or semi-transparent
                    backgroundColor = Color.Black.copy(alpha = 0.2f), // Example: semi-transparent
                    elevation = 0.dp // Remove shadow if it looks odd with gradient
                )
            },
            bottomBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "Type a message...",
                                color = textColorOnGradient.copy(alpha = 0.7f)
                            )
                        },
                        colors = TextFieldDefaults.textFieldColors(
                            textColor = textColorOnGradient,
                            backgroundColor = Color.White.copy(alpha = 0.5f), // Semi-transparent background for input
                            cursorColor = textColorOnGradient,
                            focusedIndicatorColor = Color.Transparent, // Optional: hide indicator if design calls for it
                            unfocusedIndicatorColor = Color.Transparent
                        )
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
                        enabled = currentChatId != null && llmState is LlmServiceState.Ready
                    ) {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = "Send message",
                            tint = textColorOnGradient // Ensure icon is visible
                        )
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
                items(messages.reversed(), key = { it.id }) { message ->
                    // Pass bubble and text colors to ChatMessageBubble
                    // This might require ChatMessageBubble to accept these as parameters
                    ChatMessageBubble(
                        messageText = message.text,
                        isUserMessage = message.isUserMessage,
                        bubbleColor = if (message.isUserMessage) userBubbleBackgroundColor else aiBubbleBackgroundColor,
                        textColor = if (message.isUserMessage) textOnUserBubbleColor else textOnAiBubbleColor,
                        showSpeakerIcon = !message.isUserMessage,
                        onSpeakerIconClick = {
                            Log.d("ChatScreen", "Speaker icon clicked for message: ${message.text}")
                        }
                    )
                }
            }
        }
    }
}
// ... existing LaunchedEffects and Dialogs ...
// ModelDownloadDialog might need its own background/theming if it appears over this.

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun ChatScreenPreview_ExistingChat() {
    val context = LocalContext.current
    val previewLlmService: LlmService = FakeLlmService()

    val dummyRepo = com.thingsapart.langtutor.data.ChatRepository(
        AppDatabase.getInstance(context).chatDao(), // Assuming ChatDao is accessible for preview
        previewLlmService
    )
    val dummyUserSettingsRepo = UserSettingsRepository(context)
    LangTutorAppTheme {
        ChatScreen(
            chatId = "previewChatId",
            chatRepository = dummyRepo,
            userSettingsRepository = dummyUserSettingsRepo,
            llmService = previewLlmService
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, name = "New Chat From Topic Preview")
@Composable
fun ChatScreenPreview_NewChatFromTopic() {
    val context = LocalContext.current
    val previewLlmService: LlmService = FakeLlmService(LlmServiceState.Downloading(ModelManager.DEFAULT_MODEL, 50f))

    val dummyRepo = com.thingsapart.langtutor.data.ChatRepository(
        AppDatabase.getInstance(context).chatDao(), // Assuming ChatDao is accessible for preview
        previewLlmService
    )
    val dummyUserSettingsRepo = UserSettingsRepository(context)
    LangTutorAppTheme {
        ChatScreen(
            chatId = null,
            languageCode = "es",
            topicId = "greetings",
            chatRepository = dummyRepo,
            userSettingsRepository = dummyUserSettingsRepo,
            llmService = previewLlmService
        )
    }
}