package com.thingsapart.langtutor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
    isSelected: Boolean, // Added isSelected parameter
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(150.dp) // Adjust width as needed
            .height(180.dp) // Adjust height as needed
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        elevation = 4.dp,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colors.primary) else null // Add border if selected
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
        Row { // Added Row to show both selected and unselected states
            LanguageCard(
                languageName = "Spanish",
                flagImageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9a/Flag_of_Spain.svg/1200px-Flag_of_Spain.svg.png",
                onClick = {},
                isSelected = true
            )
            Spacer(Modifier.width(8.dp))
            LanguageCard(
                languageName = "French",
                flagImageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c3/Flag_of_France.svg/1200px-Flag_of_France.svg.png",
                onClick = {},
                isSelected = false
            )
        }
    }
}
