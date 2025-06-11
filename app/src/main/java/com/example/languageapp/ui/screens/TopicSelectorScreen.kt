package com.example.languageapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.languageapp.ui.components.TopicCard
import com.example.languageapp.ui.theme.LanguageAppTheme

data class Topic(val name: String, val id: String, val imageUrl: String)

val placeholderTopics = listOf(
    Topic("Greetings", "greetings", "https://example.com/placeholder_greetings.png"),
    Topic("Food & Dining", "food", "https://example.com/placeholder_food.png"),
    Topic("Travel & Directions", "travel", "https://example.com/placeholder_travel.png"),
    Topic("Family & Friends", "family", "https://example.com/placeholder_family.png"),
    Topic("Daily Routines", "routines", "https://example.com/placeholder_routines.png"),
    Topic("Hobbies & Leisure", "hobbies", "https://example.com/placeholder_hobbies.png"),
    Topic("Work & School", "work_school", "https://example.com/placeholder_work.png"),
    Topic("Shopping", "shopping", "https://example.com/placeholder_shopping.png"),
    Topic("Health & Wellness", "health", "https://example.com/placeholder_health.png"),
    Topic("Weather", "weather", "https://example.com/placeholder_weather.png")
)

@Composable
fun TopicSelectorScreen(
    onTopicSelected: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose a Topic") },
                backgroundColor = MaterialTheme.colors.primary
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "What do you want to talk about?",
                style = MaterialTheme.typography.h5,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2), // You can change to 3 if preferred
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(placeholderTopics) { topic ->
                    TopicCard(
                        topicName = topic.name,
                        topicImageUrl = topic.imageUrl,
                        onClick = { onTopicSelected(topic.id) }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun TopicSelectorScreenPreview() {
    LanguageAppTheme {
        TopicSelectorScreen(onTopicSelected = {})
    }
}

@Preview(showBackground = true, widthDp = 480, heightDp = 800, name = "Tablet Preview")
@Composable
fun TopicSelectorScreenTabletPreview() {
    LanguageAppTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Choose a Topic") },
                    backgroundColor = MaterialTheme.colors.primary
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "What do you want to talk about?",
                    style = MaterialTheme.typography.h5,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3), // 3 columns for tablet
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(placeholderTopics.take(6)) { topic -> // Show fewer items for preview
                        TopicCard(
                            topicName = topic.name,
                            topicImageUrl = topic.imageUrl,
                            onClick = { }
                        )
                    }
                }
            }
        }
    }
}
