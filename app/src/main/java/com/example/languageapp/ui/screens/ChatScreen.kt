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
import com.example.languageapp.data.AppDatabase;
import com.example.languageapp.data.ChatRepository
import com.example.languageapp.data.UserSettingsRepository
import com.example.languageapp.data.model.ChatConversationEntity
import com.example.languageapp.data.model.ChatMessageEntity
import com.example.languageapp.ui.components.ChatMessageBubble
import com.example.languageapp.ui.theme.LanguageAppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

// Removed local data class Message, will use ChatMessageEntity

@Composable
fun ChatScreen(
    chatId: String?, // Can be null for a new chat started from topic selection
    languageCode: String? = null, // Passed when starting new chat from topic
    topicId: String? = null, // Passed when starting new chat from topic
    chatRepository: ChatRepository,
    userSettingsRepository: UserSettingsRepository // Assuming it might be needed for user info
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var currentChatId by remember { mutableStateOf(chatId) }
    var conversationTitle by remember { mutableStateOf("Chat") }

    // Load messages if chatId is available, otherwise, it's a new chat or needs initialization
    val messagesFlow: Flow<List<ChatMessageEntity>> = remember(currentChatId) {
        currentChatId?.let {
            chatRepository.getMessagesForConversation(it)
        } ?: kotlinx.coroutines.flow.flowOf(emptyList()) // Empty flow if no chatId yet
    }
    val messages by messagesFlow.collectAsState(initial = emptyList())

    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(key1 = chatId, key2 = languageCode, key3 = topicId) {
        if (chatId != null) {
            currentChatId = chatId
            // Fetch conversation title if needed, for now, use chatId or a default
            // Example: conversationTitle = chatRepository.getConversationDetails(chatId)?.conversationTitle ?: "Chat"
            conversationTitle = "Chat with Buddy" // Placeholder
        } else if (languageCode != null && topicId != null) {
            // This is a new chat initiated from topic selection
            val newConversationId = UUID.randomUUID().toString()
            currentChatId = newConversationId
            // Create a title based on language and topic
            conversationTitle = "$languageCode: $topicId" // Simple title
            val newConversation = ChatConversationEntity(
                id = newConversationId,
                targetLanguageCode = languageCode,
                topicId = topicId,
                lastMessage = null,
                lastMessageTimestamp = System.currentTimeMillis(),
                userProfileImageUrl = null, // Set a default AI buddy image if available
                conversationTitle = conversationTitle
            )
            chatRepository.startNewConversation(newConversation)
            // Optionally, send an initial AI message
            val initialAiMessage = ChatMessageEntity(
                conversationId = newConversationId,
                text = "Let's talk about $topicId in $languageCode!",
                timestamp = System.currentTimeMillis(),
                isUserMessage = false
            )
            chatRepository.sendMessage(newConversationId, initialAiMessage, newConversation)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0) // Scroll to the new message at the bottom
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
                            chatRepository.sendMessage(currentId, userMessage)
                            inputText = ""

                            // Simulate AI response
                            // kotlinx.coroutines.delay(1000)
                            val aiResponse = ChatMessageEntity(
                                conversationId = currentId,
                                text = "Simulated AI reply to: \"${userMessage.text}\"",
                                timestamp = System.currentTimeMillis() + 1, // Ensure different timestamp
                                isUserMessage = false
                            )
                            chatRepository.sendMessage(currentId, aiResponse)
                        }
                    }
                }, enabled = currentChatId != null) { // Disable if no chat is active
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
                        // TTS implementation would go here
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
    val dummyRepo = ChatRepository(AppDatabase.getInstance(context).chatDao()) // Requires context
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
    val dummyRepo = ChatRepository(AppDatabase.getInstance(context).chatDao())
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
