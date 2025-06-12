package com.thingsapart.langtutor.ui.screens

// import androidx.compose.foundation.background // No longer needed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.thingsapart.langtutor.R
import com.thingsapart.langtutor.ui.theme.LangTutorAppTheme
// Color imports for old gradient no longer needed here
// import com.thingsapart.langtutor.ui.theme.PastelRed
// import com.thingsapart.langtutor.ui.theme.PromptPastelYellow as PastelYellow
// import com.thingsapart.langtutor.ui.theme.PromptPastelGreen as PastelGreen
// import com.thingsapart.langtutor.ui.theme.PromptPastelBlue as PastelBlue
// import com.thingsapart.langtutor.ui.theme.PromptPastelPurple as PastelPurple
import com.thingsapart.langtutor.ui.components.MetallicPanelGradientBackground // New import

@Composable
fun WelcomeScreen(onNextClicked: () -> Unit) {
    MetallicPanelGradientBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // .background(brush = liquidMetalBrush) // Old background removed
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.welcome_message),
                style = MaterialTheme.typography.subtitle1,
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
