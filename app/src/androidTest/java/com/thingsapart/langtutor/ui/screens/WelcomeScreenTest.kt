package com.thingsapart.langtutor.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import com.thingsapart.langtutor.ui.theme.LanguageAppTheme
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class WelcomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun nextButton_isDisplayed_andClickable_navigates() {
        // For navigation verification, we'd ideally use a TestNavHostController
        // or mock the NavController and verify interactions.
        // For simplicity here, we'll assume click leads to some action.
        // In a full setup, you'd verify navController.navigate was called.

        val mockNavController = mock<androidx.navigation.NavController>()

        composeTestRule.setContent {
            LanguageAppTheme {
                WelcomeScreen(navController = mockNavController)
            }
        }

        composeTestRule.onNodeWithText("Next").assertIsDisplayed()
        composeTestRule.onNodeWithText("Next").performClick()

        // Verify navigation was called (basic example)
        // This requires mockNavController to be a proper mock that can be verified.
        // If using a real NavController, specific destination checks would be needed.
        verify(mockNavController).navigate("languageSelectorNative")
    }

    @Test
    fun welcomeText_isDisplayed() {
        val mockNavController = mock<androidx.navigation.NavController>()
        composeTestRule.setContent {
            LanguageAppTheme {
                WelcomeScreen(navController = mockNavController)
            }
        }
        // Check for a snippet of the long welcome text
        composeTestRule.onNodeWithText("Welcome to your new language tutor", substring = true)
            .assertIsDisplayed()
    }
}
