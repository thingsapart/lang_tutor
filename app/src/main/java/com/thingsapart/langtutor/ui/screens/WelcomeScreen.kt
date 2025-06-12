package com.thingsapart.langtutor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun WelcomeScreen(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFE0BBE4), // Light Orchid
                        Color(0xFF957DAD), // Light Slate Blue
                        Color(0xFFD291BC), // Pale Violet Red
                        Color(0xFFFEC8D8), // Light Pink
                        Color(0xFFA0D2DB)  // Light Blue
                    )
                )
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Welcome to your new language tutor, select your native language, the language you want to learn and a topic to discuss... then let's learn your language conversationally.",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = Color.White,
                modifier = Modifier.padding(bottom = 32.dp)
            )
            Button(
                onClick = { navController.navigate("languageSelectorNative") },
                enabled = true, // Enabled by default as per requirement
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text(text = "Next")
            }
        }
    }
}
