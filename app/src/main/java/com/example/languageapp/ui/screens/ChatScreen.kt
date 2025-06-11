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
import com.example.languageapp.data.AppDatabase // Keep for Preview
import com.example.languageapp.data.ChatRepository
import com.example.languageapp.data.UserSettingsRepository
import com.example.languageapp.data.model.ChatConversationEntity
import com.example.languageapp.data.model.ChatMessageEntity
import com.example.languageapp.llm.GemmaLlmService // Keep for Preview
import com.example.languageapp.ui.components.ChatMessageBubble
import com.example.languageapp.ui.theme.LanguageAppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ChatScreen(
    chatId: String?,
    languageCode: String? = null,
    topicId: String? = null,
    chatRepository: ChatRepository,
    userSettingsRepository: UserSettingsRepository // Assuming it might be needed for user info
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

    LaunchedEffect(key1 = chatId, key2 = languageCode, key3 = topicId) {
        if (chatId != null) {
            currentChatId = chatId
            // TODO: Fetch actual conversation title from repository
            // conversationTitle = chatRepository.getConversationDetails(chatId)?.conversationTitle ?: "Chat"
            conversationTitle = "Chat with Buddy" // Placeholder
        } else if (languageCode != null && topicId != null) {
            val newConversationId = UUID.randomUUID().toString()
            currentChatId = newConversationId
            conversationTitle = "$languageCode: $topicId" // Simple title
            val newConversation = ChatConversationEntity(
                id = newConversationId,
                targetLanguageCode = languageCode,
                topicId = topicId,
                lastMessage = null, // Will be updated by AI's first message
                lastMessageTimestamp = System.currentTimeMillis(), // Will be updated
                userProfileImageUrl = null,
                conversationTitle = conversationTitle
            )
            // This will now also trigger the initial AI message via LlmService
            chatRepository.startNewConversation(newConversation)
            // The initial AI message is no longer sent explicitly from here.
            // It's handled by ChatRepository.startNewConversation -> LlmService.getInitialGreeting
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            // Scroll to the new message (which is at the end of the original list,
            // but appears at the top due to reverseLayout = true and items(messages.reversed()))
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(conversationTitle) },
                backgroundColor = MaterialTheme.colors.primary
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = MaterialTheme.colors.surface
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = {
                    val currentId = currentChatId
                    if (inputText.isNotBlank() && currentId != null) {
                        val userMessage = ChatMessageEntity(
                            conversationId = currentId,
                            text = inputText,
                            timestamp = System.currentTimeMillis(),
                            isUserMessage = true
                        )
                        coroutineScope.launch {
                            // ChatRepository.sendMessage will now also trigger the AI response
                            chatRepository.sendMessage(currentId, userMessage)
                            inputText = ""
                            // The simulated AI response block below is now removed.
                        }
                    }
                }, enabled = currentChatId != null) {
                    Icon(Icons.Filled.Send, contentDescription = "Send message")
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 8.dp),
            reverseLayout = true, // Newest messages at the bottom of the screen
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom)
        ) {
            // messages are fetched chronologically (oldest first).
            // .reversed() makes it newest first for display with reverseLayout.
            items(messages.reversed()) { message ->
                ChatMessageBubble(
                    messageText = message.text,
                    isUserMessage = message.isUserMessage,
                    showSpeakerIcon = !message.isUserMessage,
                    onSpeakerIconClick = {
                        Log.d("ChatScreen", "Speaker icon clicked for message: ${message.text}")
                        // TTS implementation would go here
                    }
                )
            }
        }
    }
}

// Previews might need adjustment due to ChatRepository constructor change
// Adding LlmService to ChatRepository, which is needed for Preview

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun ChatScreenPreview_ExistingChat() {
    val context = LocalContext.current
    // GemmaLlmService now requires context, provide it.
    val dummyLlmService = GemmaLlmService(context)
    val dummyRepo = ChatRepository(AppDatabase.getInstance(context).chatDao(), dummyLlmService)
    val dummyUserSettingsRepo = UserSettingsRepository(context)
    LanguageAppTheme {
        ChatScreen(
            chatId = "previewChatId",
            chatRepository = dummyRepo,
            userSettingsRepository = dummyUserSettingsRepo
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, name = "New Chat From Topic Preview")
@Composable
fun ChatScreenPreview_NewChatFromTopic() {
    val context = LocalContext.current
    val dummyLlmService = GemmaLlmService(context)
    val dummyRepo = ChatRepository(AppDatabase.getInstance(context).chatDao(), dummyLlmService)
    val dummyUserSettingsRepo = UserSettingsRepository(context)
    LanguageAppTheme {
        ChatScreen(
            chatId = null,
            languageCode = "es",
            topicId = "greetings",
            chatRepository = dummyRepo,
            userSettingsRepository = dummyUserSettingsRepo
        )
    }
}
