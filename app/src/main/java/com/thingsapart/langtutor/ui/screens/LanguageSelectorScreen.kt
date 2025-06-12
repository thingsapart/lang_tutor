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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.thingsapart.langtutor.ui.components.LanguageCard
import com.thingsapart.langtutor.ui.theme.LanguageAppTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.ButtonDefaults

data class Language(val name: String, val code: String, val flagImageUrl: String)

val nativeLanguages = listOf(
    Language("English", "en", "https://flagcdn.com/w320/gb.png"),
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
    // Add more native languages if needed
)

val learnableLanguages = listOf(
    Language("English", "en", "https://flagcdn.com/w320/gb.png"),
    Language("German", "de", "https://flagcdn.com/w320/de.png"),
    Language("Spanish", "es", "https://flagcdn.com/w320/es.png"),
    Language("Japanese", "ja", "https://flagcdn.com/w320/jp.png"),
    Language("Chinese", "zh", "https://flagcdn.com/w320/cn.png"),
    Language("Korean", "ko", "https://flagcdn.com/w320/kr.png"),
    Language("Norwegian", "no", "https://flagcdn.com/w320/no.png"),
    Language("Swedish", "se", "https://flagcdn.com/w320/se.png")
)

@Composable
fun LanguageSelectorScreen(
    isNativeSelection: Boolean,
    onLanguageSelected: (String) -> Unit
) {
    var selectedLanguageCode by remember { mutableStateOf<String?>(null) }
    val languages = if (isNativeSelection) nativeLanguages else learnableLanguages
    val title = if (isNativeSelection) "Select Your Native Language" else "Select Language to Learn"
    val caption = if (isNativeSelection) "Select your native language" else "Now select the language you want to learn"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                backgroundColor = MaterialTheme.colors.primary
            )
        },
        floatingActionButton = {
            if (selectedLanguageCode != null) {
                FloatingActionButton(
                    onClick = { selectedLanguageCode?.let { onLanguageSelected(it) } },
                    backgroundColor = MaterialTheme.colors.primary
                ) {
                    Icon(Icons.Filled.ArrowForward, "Next", tint = Color.White)
                }
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp), // Outer padding
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = caption,
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
                items(languages) { language ->
                    LanguageCard(
                        languageName = language.name,
                        flagImageUrl = language.flagImageUrl,
                        isSelected = selectedLanguageCode == language.code,
                        onClick = { selectedLanguageCode = language.code }
                    )
                }
            }
            // Removed explicit Spacer and Button, using FAB instead.
        }
    }
}

@Preview(showBackground = true, name = "Native Language Selection")
@Composable
fun LanguageSelectorScreenNativePreview() {
    LanguageAppTheme {
        LanguageSelectorScreen(isNativeSelection = true, onLanguageSelected = {})
    }
}

@Preview(showBackground = true, name = "Learn Language Selection")
@Composable
fun LanguageSelectorScreenLearnPreview() {
    LanguageAppTheme {
        LanguageSelectorScreen(isNativeSelection = false, onLanguageSelected = {})
    }
}
