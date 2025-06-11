package com.thingsapart.langtutor.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.languageapp.ui.theme.LanguageAppTheme

@Composable
fun ChatListItem(
    userName: String,
    lastMessage: String,
    timestamp: String,
    userImageUrl: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (userImageUrl != null) {
            Image(
                painter = rememberAsyncImagePainter(model = userImageUrl),
                contentDescription = "$userName profile picture",
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            // Placeholder for when no image is available
            Spacer(modifier = Modifier.size(50.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = userName,
                style = MaterialTheme.typography.subtitle1,
                fontWeight = MaterialTheme.typography.subtitle1.fontWeight // Ensure it's bold if desired by theme
            )
            Text(
                text = lastMessage,
                style = MaterialTheme.typography.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f) // Softer color for last message
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = timestamp,
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f) // Even softer for timestamp
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ChatListItemPreview() {
    LanguageAppTheme {
        ChatListItem(
            userName = "Alex Linderson",
            lastMessage = "Hey, how are you doing today? I was wondering if you'd like to practice Spanish.",
            timestamp = "10:35 AM",
            userImageUrl = "https://example.com/user_avatar.png", // Replace with a real placeholder
            onClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ChatListItemNoImagePreview() {
    LanguageAppTheme {
        ChatListItem(
            userName = "Maria Rodriguez",
            lastMessage = "¡Hola! ¿Qué tal?",
            timestamp = "Yesterday",
            userImageUrl = null,
            onClick = {}
        )
    }
}
