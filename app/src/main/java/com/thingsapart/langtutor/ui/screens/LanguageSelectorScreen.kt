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
import com.thingsapart.langtutor.ui.components.LanguageCard
import com.thingsapart.langtutor.ui.theme.LanguageAppTheme

data class Language(val name: String, val code: String, val flagImageUrl: String)

// Removed placeholderLanguages list

@Composable
fun LanguageSelectorScreen(
    title: String,
    caption: String,
    languages: List<Language>,
    onLanguageSelected: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) }, // Use title parameter
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
                text = caption, // Use caption parameter
                style = MaterialTheme.typography.h5,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2), // You can change to 3 if preferred
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(languages) { language -> // Use languages parameter
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
        LanguageSelectorScreen(
            title = "Native Language",
            caption = "Select your native language",
            languages = listOf(
                Language("English", "en", "https://flagcdn.com/w320/us.png"),
                Language("German", "de", "https://flagcdn.com/w320/de.png"),
                Language("Spanish", "es", "https://flagcdn.com/w320/es.png")
            ),
            onLanguageSelected = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 480, heightDp = 800, name = "Tablet Preview")
@Composable
fun LanguageSelectorScreenTabletPreview() {
    LanguageAppTheme {
        LanguageSelectorScreen(
            title = "Learn Language",
            caption = "Select a language to learn",
            languages = listOf(
                Language("French", "fr", "https://flagcdn.com/w320/fr.png"),
                Language("Italian", "it", "https://flagcdn.com/w320/it.png"),
                Language("Japanese", "ja", "https://flagcdn.com/w320/jp.png"),
                Language("Korean", "ko", "https://flagcdn.com/w320/kr.png")
            ),
            onLanguageSelected = {}
        )
    }
}
