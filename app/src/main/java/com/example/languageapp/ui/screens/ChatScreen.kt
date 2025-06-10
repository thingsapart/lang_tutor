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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.languageapp.ui.components.ChatMessageBubble
import com.example.languageapp.ui.theme.LanguageAppTheme
import kotlinx.coroutines.launch

data class Message(
    val id: String,
    val text: String,
    val isUserMessage: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun ChatScreen(chatId: String?) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Placeholder for chat messages
    val messages = remember { mutableStateListOf<Message>() }
    var inputText by remember { mutableStateOf("") }

    // Add initial messages for preview or if it's a new chat
    LaunchedEffect(key1 = chatId) {
        if (chatId == null || messages.isEmpty()) { // Simple check for new chat or empty state
            messages.addAll(
                listOf(
                    Message("1", "¡Hola! ¿Cómo estás?", false),
                    Message("2", "Estoy bien, gracias. ¿Y tú?", true),
                    Message("3", "Muy bien. ¿Qué te gustaría practicar hoy?", false)
                )
            )
        }
        // Scroll to the bottom when messages are loaded/updated
        if (messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(messages.size -1)
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chatId ?: "New Chat") }, // Display chatId or "New Chat"
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
                    if (inputText.isNotBlank()) {
                        val userMessage = Message(
                            id = (messages.size + 1).toString(),
                            text = inputText,
                            isUserMessage = true
                        )
                        messages.add(userMessage)
                        inputText = ""

                        // Simulate AI response
                        coroutineScope.launch {
                            // Simulate delay
                            // kotlinx.coroutines.delay(1000) // Requires kotlinx-coroutines-core
                            val aiResponse = Message(
                                id = (messages.size + 1).toString(),
                                text = "That's interesting! Tell me more.",
                                isUserMessage = false
                            )
                            messages.add(aiResponse)
                            if (messages.isNotEmpty()) {
                                listState.animateScrollToItem(messages.size -1)
                            }
                        }
                    }
                }) {
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
            reverseLayout = true, // New messages appear at the bottom
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages.reversed()) { message -> // Display messages in correct order
                ChatMessageBubble(
                    messageText = message.text,
                    isUserMessage = message.isUserMessage,
                    showSpeakerIcon = !message.isUserMessage, // Example: Show for AI messages
                    onSpeakerIconClick = {
                        // Handle speaker icon click, e.g., Log
                        Log.d("ChatScreen", "Speaker icon clicked for message: ${message.text}")
                        // In a real app, you would trigger Text-to-Speech here
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun ChatScreenPreview() {
    LanguageAppTheme {
        ChatScreen(chatId = "previewChatId")
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, name = "New Chat Preview")
@Composable
fun NewChatScreenPreview() {
    LanguageAppTheme {
        ChatScreen(chatId = null)
    }
}
