package com.example.languageapp.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.languageapp.ui.components.ChatListItem
import com.example.languageapp.ui.theme.LanguageAppTheme

data class Chat(
    val id: String,
    val userName: String,
    val lastMessage: String,
    val timestamp: String,
    val userImageUrl: String?
)

val placeholderChats = listOf(
    Chat(
        id = "chat1",
        userName = "Maria (Spanish Tutor)",
        lastMessage = "¡Hola! ¿Cómo estás? Ready for our next lesson?",
        timestamp = "11:45 AM",
        userImageUrl = "https://example.com/maria_avatar.png" // Replace with actual or better placeholder
    ),
    Chat(
        id = "chat2",
        userName = "Kenji (Japanese Practice)",
        lastMessage = "こんにちは！今日の天気はいいですね。",
        timestamp = "Yesterday",
        userImageUrl = null // Example of no image
    ),
    Chat(
        id = "chat3",
        userName = "French Cafe Group",
        lastMessage = "Salut tout le monde! Qui est disponible pour un café demain?",
        timestamp = "Mon",
        userImageUrl = "https://example.com/french_group_icon.png"
    ),
    Chat(
        id = "chat4",
        userName = "AI Language Buddy",
        lastMessage = "I can help you practice vocabulary for your upcoming trip!",
        timestamp = "Sun",
        userImageUrl = "https://example.com/ai_buddy_icon.png"
    )
)

@Composable
fun OngoingChatsScreen(
    onChatSelected: (String) -> Unit
) {
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
                .padding(top = 16.dp), // Add some padding at the top of the column content
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Your Recent Chats",
                style = MaterialTheme.typography.h5,
                modifier = Modifier.padding(bottom = 16.dp) // Padding below the title
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(placeholderChats) { chat ->
                    ChatListItem(
                        userName = chat.userName,
                        lastMessage = chat.lastMessage,
                        timestamp = chat.timestamp,
                        userImageUrl = chat.userImageUrl,
                        onClick = { onChatSelected(chat.id) }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun OngoingChatsScreenPreview() {
    LanguageAppTheme {
        OngoingChatsScreen(onChatSelected = {})
    }
}
