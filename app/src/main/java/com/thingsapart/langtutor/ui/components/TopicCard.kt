package com.thingsapart.langtutor.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.thingsapart.langtutor.ui.theme.LangTutorAppTheme

@Composable
fun TopicCard(
    topicName: String,
    topicImageUrl: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(180.dp) // Adjust width as needed
            .height(150.dp) // Adjust height as needed
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = topicImageUrl),
                contentDescription = "$topicName image",
                modifier = Modifier
                    .size(60.dp) // Adjust image size as needed
                    .padding(bottom = 8.dp),
                contentScale = ContentScale.Crop
            )
            Text(
                text = topicName,
                style = MaterialTheme.typography.subtitle1,
                maxLines = 2
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TopicCardPreview() {
    LangTutorAppTheme {
        TopicCard(
            topicName = "Common Greetings",
            topicImageUrl = "https://example.com/greeting_icon.png", // Replace with a real placeholder or remove if none
            onClick = {}
        )
    }
}
