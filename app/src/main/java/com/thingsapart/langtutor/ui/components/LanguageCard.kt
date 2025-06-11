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
import com.thingsapart.langtutor.ui.theme.LanguageAppTheme

@Composable
fun LanguageCard(
    languageName: String,
    flagImageUrl: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(150.dp) // Adjust width as needed
            .height(180.dp) // Adjust height as needed
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
                painter = rememberAsyncImagePainter(model = flagImageUrl),
                contentDescription = "$languageName flag",
                modifier = Modifier
                    .size(80.dp) // Adjust image size as needed
                    .padding(bottom = 8.dp),
                contentScale = ContentScale.Crop
            )
            Text(
                text = languageName,
                style = MaterialTheme.typography.subtitle1
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LanguageCardPreview() {
    LanguageAppTheme {
        LanguageCard(
            languageName = "Spanish",
            flagImageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9a/Flag_of_Spain.svg/1200px-Flag_of_Spain.svg.png", // Example URL
            onClick = {}
        )
    }
}
