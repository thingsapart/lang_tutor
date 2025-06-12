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
// import com.thingsapart.langtutor.ui.theme.PastelBlue // No longer used directly here
import androidx.compose.foundation.shape.RoundedCornerShape // For Surface shape
import androidx.compose.ui.graphics.Color // For new parameters
import com.thingsapart.langtutor.ui.theme.LangTutorAppTheme

@Composable
fun ChatMessageBubble(
    messageText: String,
    isUserMessage: Boolean,
    bubbleColor: Color, // New parameter
    textColor: Color,   // New parameter
    showSpeakerIcon: Boolean = false, // Default value provided
    onSpeakerIconClick: () -> Unit = {} // Default value provided
) {
    // val bubbleColor = if (isUserMessage) MaterialTheme.colors.primary else PastelBlue // Old logic
    val alignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart
    // val textColor = if (isUserMessage) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface // Old logic

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp), // Keep vertical padding for spacing between bubbles
        contentAlignment = alignment
    ) {
        Surface( // Changed from Card to Surface as per prompt example
            shape = RoundedCornerShape(8.dp), // Consistent with prompt example
            // elevation = 1.dp, // Consider removing or reducing elevation
            color = bubbleColor, // Use parameter
            modifier = Modifier.widthIn(max = 300.dp) // Max width for a bubble
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = messageText,
                    style = MaterialTheme.typography.body1, // Consider MaterialTheme.typography.bodyMedium or bodyLarge from M3
                    color = textColor,   // Use parameter
                    modifier = Modifier.weight(1f, fill = false) // Important for text to not push icon
                )
                if (showSpeakerIcon) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = "Play message audio",
                        tint = textColor.copy(alpha = 0.7f), // Tint based on new textColor
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
    LangTutorAppTheme {
        ChatMessageBubble(
            messageText = "Hello, AI! How are you?",
            isUserMessage = true,
            bubbleColor = Color(0xCCADD8E6), // Example UserBubbleColor
            textColor = Color(0xFF121212),   // Example SomeDarkColorForText
            showSpeakerIcon = true,
            onSpeakerIconClick = {}
        )
    }
}

@Preview(showBackground = true, name = "AI Message")
@Composable
fun AiChatMessageBubblePreview() {
    LangTutorAppTheme {
        Column {
            ChatMessageBubble(
                messageText = "Hello, User! I am doing great. How can I help you today?",
                isUserMessage = false,
                bubbleColor = Color(0xCCFFB6C1), // Example AiBubbleColor
                textColor = Color(0xFF121212),   // Example SomeDarkColorForText
                showSpeakerIcon = true,
                onSpeakerIconClick = {}
            )
            ChatMessageBubble(
                messageText = "This is a longer message from the AI to check how text wrapping behaves within the defined constraints of the message bubble.",
                isUserMessage = false,
                bubbleColor = Color(0xCCFFB6C1),
                textColor = Color(0xFF121212),
                showSpeakerIcon = false,
                onSpeakerIconClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "User Message No Icon")
@Composable
fun UserChatMessageBubbleNoIconPreview() {
    LangTutorAppTheme {
        ChatMessageBubble(
            messageText = "Just a simple message.",
            isUserMessage = true,
            bubbleColor = Color(0xCCADD8E6),
            textColor = Color(0xFF121212),
            showSpeakerIcon = false,
            onSpeakerIconClick = {}
        )
    }
}
