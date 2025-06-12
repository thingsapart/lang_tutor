package com.thingsapart.langtutor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow // Changed from ArrowForward for "Let's Start"
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.thingsapart.langtutor.ui.components.TopicCard
import com.thingsapart.langtutor.ui.theme.LanguageAppTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth


data class Topic(val name: String, val id: String, val imageUrl: String)

// Updated list of topics
val engagingTopics = listOf(
    Topic("Ordering Food", "ordering_food", "https://images.pexels.com/photos/1640777/pexels-photo-1640777.jpeg?auto=compress&cs=tinysrgb&dpr=2&h=750&w=1260"), // Placeholder image
    Topic("Talking Hobbies", "hobbies", "https://images.pexels.com/photos/265076/pexels-photo-265076.jpeg?auto=compress&cs=tinysrgb&dpr=2&h=750&w=1260"), // Placeholder image
    Topic("Travel Plans", "travel_plans", "https://images.pexels.com/photos/2108845/pexels-photo-2108845.jpeg?auto=compress&cs=tinysrgb&dpr=2&h=750&w=1260"), // Placeholder image
    Topic("Favorite Movies", "favorite_movies", "https://images.pexels.com/photos/7991579/pexels-photo-7991579.jpeg?auto=compress&cs=tinysrgb&dpr=2&h=750&w=1260"), // Placeholder image
    Topic("Weekend Activities", "weekend_activities", "https://images.pexels.com/photos/1171084/pexels-photo-1171084.jpeg?auto=compress&cs=tinysrgb&dpr=2&h=750&w=1260"), // Placeholder image
    Topic("Greetings", "greetings", "https://images.pexels.com/photos/4195325/pexels-photo-4195325.jpeg?auto=compress&cs=tinysrgb&dpr=2&h=750&w=1260"),
    Topic("Family & Friends", "family", "https://images.pexels.com/photos/1081031/pexels-photo-1081031.jpeg?auto=compress&cs=tinysrgb&dpr=2&h=750&w=1260")
)

@Composable
fun TopicSelectorScreen(
    onTopicSelected: (String) -> Unit // This callback is now triggered by the "Let's Start" button
) {
    var selectedTopicId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose a Topic") },
                backgroundColor = MaterialTheme.colors.primary
            )
        },
        floatingActionButton = {
            if (selectedTopicId != null) {
                FloatingActionButton(
                    onClick = { selectedTopicId?.let { onTopicSelected(it) } },
                    backgroundColor = MaterialTheme.colors.primary,
                ) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Icon(Icons.Filled.PlayArrow, "Let's Start", tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Let's Start", color = Color.White)
                    }
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center // Centered FAB for "Let's Start"
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp), // Outer padding
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "What do you want to talk about?",
                style = MaterialTheme.typography.h5,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(8.dp), // Padding around the grid items
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f) // Ensure grid takes available space
            ) {
                items(engagingTopics) { topic ->
                    TopicCard(
                        topicName = topic.name,
                        topicImageUrl = topic.imageUrl,
                        isSelected = selectedTopicId == topic.id,
                        onClick = { selectedTopicId = topic.id }
                    )
                }
            }
             // Spacer and Button removed, using FAB instead
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TopicSelectorScreenPreview() {
    LanguageAppTheme {
        TopicSelectorScreen(onTopicSelected = {})
    }
}

// Removed the Tablet Preview for brevity, the main preview should suffice.
// If specific tablet layout adjustments were needed, it would be kept.
