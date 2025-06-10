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
import com.example.languageapp.ui.components.LanguageCard
import com.example.languageapp.ui.theme.LanguageAppTheme

data class Language(val name: String, val code: String, val flagImageUrl: String)

val placeholderLanguages = listOf(
    Language("Spanish", "es", "https://flagcdn.com/w320/es.png"),
    Language("French", "fr", "https://flagcdn.com/w320/fr.png"),
    Language("German", "de", "https://flagcdn.com/w320/de.png"),
    Language("Italian", "it", "https://flagcdn.com/w320/it.png"),
    Language("Portuguese", "pt", "https://flagcdn.com/w320/pt.png"),
    Language("Japanese", "ja", "https://flagcdn.com/w320/jp.png"),
    Language("Korean", "ko", "https://flagcdn.com/w320/kr.png"),
    Language("Chinese", "zh", "https://flagcdn.com/w320/cn.png"),
    Language("Hindi", "hi", "https://flagcdn.com/w320/in.png"),
    Language("Arabic", "ar", "https://flagcdn.com/w320/sa.png")
)

@Composable
fun LanguageSelectorScreen(
    onLanguageSelected: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose Your Language") },
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
                text = "What language do you want to learn?",
                style = MaterialTheme.typography.h5,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2), // You can change to 3 if preferred
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(placeholderLanguages) { language ->
                    LanguageCard(
                        languageName = language.name,
                        flagImageUrl = language.flagImageUrl,
                        onClick = { onLanguageSelected(language.code) }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun LanguageSelectorScreenPreview() {
    LanguageAppTheme {
        LanguageSelectorScreen(onLanguageSelected = {})
    }
}

@Preview(showBackground = true, widthDp = 480, heightDp = 800, name = "Tablet Preview")
@Composable
fun LanguageSelectorScreenTabletPreview() {
    LanguageAppTheme {
        // Example with 3 columns for a tablet-like preview
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Choose Your Language") },
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
                    text = "What language do you want to learn?",
                    style = MaterialTheme.typography.h5,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3), // 3 columns for tablet
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(placeholderLanguages.take(6)) { language -> // Show fewer items for preview clarity
                        LanguageCard(
                            languageName = language.name,
                            flagImageUrl = language.flagImageUrl,
                            onClick = { }
                        )
                    }
                }
            }
        }
    }
}
