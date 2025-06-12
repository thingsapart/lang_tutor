package com.thingsapart.langtutor.ui.screens

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
import com.thingsapart.langtutor.ui.components.TopicCard
import com.thingsapart.langtutor.ui.theme.LangTutorAppTheme

data class Topic(val name: String, val id: String, val imageUrl: String)

val funTopics = listOf(
    Topic("Movies & TV Shows", "movies_tv", "https://example.com/placeholder_movies.png"),
    Topic("Music & Concerts", "music", "https://example.com/placeholder_music.png"),
    Topic("Favorite Foods & Cooking", "favorite_foods", "https://example.com/placeholder_cooking.png"),
    Topic("Sports & Fitness", "sports_fitness", "https://example.com/placeholder_sports.png"),
    Topic("Video Games & Gaming", "video_games", "https://example.com/placeholder_gaming.png"),
    Topic("Books & Literature", "books", "https://example.com/placeholder_books.png"),
    Topic("Dream Vacation Spots", "dream_vacations", "https://example.com/placeholder_vacation.png"),
    Topic("Interesting Hobbies", "hobbies_new", "https://example.com/placeholder_hobbies_new.png"),
    Topic("Future Technology", "future_tech", "https://example.com/placeholder_tech.png"),
    Topic("Funny Childhood Stories", "childhood_stories", "https://example.com/placeholder_childhood.png"),
    Topic("Learning New Skills", "new_skills", "https://example.com/placeholder_skills.png"),
    Topic("Weekend Plans", "weekend_plans", "https://example.com/placeholder_weekend.png")
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
                items(funTopics) { topic -> // Use funTopics
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
    LangTutorAppTheme {
        TopicSelectorScreen(onTopicSelected = {})
    }
}

@Preview(showBackground = true, widthDp = 480, heightDp = 800, name = "Tablet Preview")
@Composable
fun TopicSelectorScreenTabletPreview() {
    LangTutorAppTheme {
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
                    items(funTopics.take(6)) { topic -> // Use funTopics for preview
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
