package com.thingsapart.langtutor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.thingsapart.langtutor.R
import com.thingsapart.langtutor.ui.theme.LangTutorAppTheme
// Import the correct color names based on Color.kt
import com.thingsapart.langtutor.ui.theme.PastelRed // This one was added directly
import com.thingsapart.langtutor.ui.theme.PromptPastelYellow as PastelYellow // Alias to match original intent
import com.thingsapart.langtutor.ui.theme.PromptPastelGreen as PastelGreen   // Alias to match original intent
import com.thingsapart.langtutor.ui.theme.PromptPastelBlue as PastelBlue     // Alias to match original intent
import com.thingsapart.langtutor.ui.theme.PromptPastelPurple as PastelPurple // Alias to match original intent

@Composable
fun WelcomeScreen(onNextClicked: () -> Unit) {
    val liquidMetalBrush = Brush.linearGradient(
        colors = listOf(
            PastelRed,
            PastelYellow,
            PastelGreen,
            PastelBlue,
            PastelPurple,
            PastelRed // Loop back to the start for a smoother gradient feel
        ),
        // Adjust startX, startY, endX, endY for different gradient directions if desired
        // For a more "liquid" feel, a more complex brush (e.g., radial or sweep) or animation might be needed later.
    )

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = liquidMetalBrush) // Apply the gradient to the Column background
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.welcome_message),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 24.dp),
                color = Color.Black // Ensure text is readable on the gradient
            )

            Button(
                onClick = onNextClicked,
                // Button is enabled by default as no selection is made on this screen
            ) {
                Text(stringResource(id = R.string.next_button_text))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun WelcomeScreenPreview() {
    LangTutorAppTheme {
        WelcomeScreen(onNextClicked = {})
    }
}
