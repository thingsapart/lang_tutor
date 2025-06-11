package com.thingsapart.langtutor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.languageapp.ui.theme.LanguageAppTheme
import com.example.languageapp.ui.theme.PastelBlue

@Composable
fun ChatMessageBubble(
    messageText: String,
    isUserMessage: Boolean,
    showSpeakerIcon: Boolean,
    onSpeakerIconClick: () -> Unit
) {
    val bubbleColor = if (isUserMessage) MaterialTheme.colors.primary else PastelBlue
    val alignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart
    val textColor = if (isUserMessage) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp), // Max width for a bubble
            shape = MaterialTheme.shapes.medium,
            backgroundColor = bubbleColor,
            elevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = messageText,
                    style = MaterialTheme.typography.body1,
                    color = textColor,
                    modifier = Modifier.weight(1f, fill = false) // Important for text to not push icon
                )
                if (showSpeakerIcon) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = "Play message audio",
                        tint = textColor.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(onClick = onSpeakerIconClick)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "User Message")
@Composable
fun UserChatMessageBubblePreview() {
    LanguageAppTheme {
        ChatMessageBubble(
            messageText = "Hello, AI! How are you?",
            isUserMessage = true,
            showSpeakerIcon = true,
            onSpeakerIconClick = {}
        )
    }
}

@Preview(showBackground = true, name = "AI Message")
@Composable
fun AiChatMessageBubblePreview() {
    LanguageAppTheme {
        Column {
            ChatMessageBubble(
                messageText = "Hello, User! I am doing great. How can I help you today?",
                isUserMessage = false,
                showSpeakerIcon = true,
                onSpeakerIconClick = {}
            )
            ChatMessageBubble(
                messageText = "This is a longer message from the AI to check how text wrapping behaves within the defined constraints of the message bubble.",
                isUserMessage = false,
                showSpeakerIcon = false,
                onSpeakerIconClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "User Message No Icon")
@Composable
fun UserChatMessageBubbleNoIconPreview() {
    LanguageAppTheme {
        ChatMessageBubble(
            messageText = "Just a simple message.",
            isUserMessage = true,
            showSpeakerIcon = false,
            onSpeakerIconClick = {}
        )
    }
}
