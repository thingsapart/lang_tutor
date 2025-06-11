package com.thingsapart.langtutor.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thingsapart.langtutor.data.model.ChatConversationEntity
import com.thingsapart.langtutor.ui.components.ChatListItem
import com.thingsapart.langtutor.ui.theme.LanguageAppTheme
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

// Removed placeholder data class and list, will use entities from DB

@Composable
fun OngoingChatsScreen(
    chatRepository: com.thingsapart.langtutor.data.ChatRepository, // Added repository parameter
    onChatSelected: (String) -> Unit
) {
    val conversationsFlow: Flow<List<ChatConversationEntity>> = chatRepository.getAllConversations()
    val conversations by conversationsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Continue a Conversation") },
                backgroundColor = MaterialTheme.colors.primary
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Your Recent Chats",
                style = MaterialTheme.typography.h5,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (conversations.isEmpty()) {
                Text(
                    text = "No ongoing chats yet. Start a new one!",
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    conversations.forEach { conversation ->
                        ChatListItem(
                            userName = conversation.conversationTitle, // Using conversationTitle for userName
                            lastMessage = conversation.lastMessage ?: "",
                            timestamp = formatTimestamp(conversation.lastMessageTimestamp),
                            userImageUrl = conversation.userProfileImageUrl,
                            onClick = { onChatSelected(conversation.id) }
                        )
                    }
                }
            }
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun OngoingChatsScreenPreview() {
    // For preview, we can't easily provide a real ChatRepository.
    // So, we'll show an empty state or pass mock data if ChatListItem is adapted.
    // For this iteration, let's assume an empty state for simplicity in preview.
    val placeholderChatsPreview = listOf(
        ChatConversationEntity(
            id = "chat1",
            targetLanguageCode = "es",
            topicId = "greetings",
            conversationTitle = "Maria (Spanish Tutor)",
            lastMessage = "¡Hola! ¿Cómo estás? Ready for our next lesson?",
            lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 15, // 15 mins ago
            userProfileImageUrl = null
        ),
        ChatConversationEntity(
            id = "chat2",
            targetLanguageCode = "ja",
            topicId = "food",
            conversationTitle = "Kenji (Japanese Practice)",
            lastMessage = "こんにちは！今日の天気はいいですね。",
            lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 24, // Yesterday
            userProfileImageUrl = null
        )
    )
    LanguageAppTheme {
         Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Continue a Conversation") },
                    backgroundColor = MaterialTheme.colors.primary
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Your Recent Chats",
                    style = MaterialTheme.typography.h5,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    placeholderChatsPreview.forEach { conversation ->
                        ChatListItem(
                            userName = conversation.conversationTitle,
                            lastMessage = conversation.lastMessage ?: "",
                            timestamp = formatTimestamp(conversation.lastMessageTimestamp),
                            userImageUrl = conversation.userProfileImageUrl,
                            onClick = { }
                        )
                    }
                }
            }
        }
    }
}
