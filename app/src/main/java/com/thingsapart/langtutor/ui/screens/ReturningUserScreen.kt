package com.thingsapart.langtutor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thingsapart.langtutor.ui.theme.LangTutorAppTheme
import com.thingsapart.langtutor.ui.theme.LangTutorAppTheme

@Composable
fun ReturningUserScreen(
    onContinueChatClicked: () -> Unit,
    onSelectTopicClicked: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Welcome Back!") },
                backgroundColor = MaterialTheme.colors.primary
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome back!",
                style = MaterialTheme.typography.h4.copy(fontSize = 32.sp), // Larger welcome
                modifier = Modifier.padding(bottom = 48.dp)
            )

            Button(
                onClick = onContinueChatClicked,
                modifier = Modifier
                    .fillMaxWidth(0.8f) // Button takes 80% of screen width
                    .height(60.dp)
                    .padding(bottom = 24.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.secondary)
            ) {
                Text("Continue Previous Chat", fontSize = 18.sp)
            }

            Button(
                onClick = onSelectTopicClicked,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(60.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Start New Chat by Topic", fontSize = 18.sp)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun ReturningUserScreenPreview() {
    LangTutorAppTheme {
        ReturningUserScreen(
            onContinueChatClicked = {},
            onSelectTopicClicked = {}
        )
    }
}
