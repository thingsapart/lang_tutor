package com.thingsapart.langtutor.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.thingsapart.langtutor.ui.theme.LanguageAppTheme
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class LanguageSelectorScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Test Native Language Selection Mode
    @Test
    fun nativeMode_captionDisplayed_nextButtonDisabledInitially_thenEnabled() {
        val onLanguageSelectedMock = mock<(String) -> Unit>()

        composeTestRule.setContent {
            LanguageAppTheme {
                LanguageSelectorScreen(
                    isNativeSelection = true,
                    onLanguageSelected = onLanguageSelectedMock
                )
            }
        }

        // Check caption
        composeTestRule.onNodeWithText("Select your native language").assertIsDisplayed()
        // Check TopAppBar title
        composeTestRule.onNodeWithText("Select Your Native Language").assertIsDisplayed()


        // "Next" button (FAB) should not exist initially / be disabled
        // The FAB is only added to composition if a language is selected.
        composeTestRule.onNodeWithContentDescription("Next").assertDoesNotExist()


        // Select a language (assuming "English" is one of the native languages)
        // This requires LanguageCard to be identifiable, e.g., by language name
        composeTestRule.onNodeWithText("English", useUnmergedTree = true).performClick()

        // "Next" button (FAB) should now be displayed and enabled
        composeTestRule.onNodeWithContentDescription("Next").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Next").assertIsEnabled()
        composeTestRule.onNodeWithContentDescription("Next").performClick()

        // Verify that the callback was called with the correct language code
        verify(onLanguageSelectedMock).invoke("en") // Assuming "en" is the code for English
    }

    // Test Learning Language Selection Mode
    @Test
    fun learnMode_captionDisplayed_languageListCorrect_nextButtonLogic() {
        val onLanguageSelectedMock = mock<(String) -> Unit>()

        composeTestRule.setContent {
            LanguageAppTheme {
                LanguageSelectorScreen(
                    isNativeSelection = false, // Learn mode
                    onLanguageSelected = onLanguageSelectedMock
                )
            }
        }

        // Check caption
        composeTestRule.onNodeWithText("Now select the language you want to learn").assertIsDisplayed()
        // Check TopAppBar title
        composeTestRule.onNodeWithText("Select Language to Learn").assertIsDisplayed()

        // Check for a language specific to the learnable list (e.g., Norwegian)
        // and one that might be only in native (e.g., Hindi, if lists differ significantly)
        composeTestRule.onNodeWithText("Norwegian").assertIsDisplayed() // From learnableLanguages
        // Assuming "Hindi" is only in nativeLanguages for this test to be meaningful
        // If lists are similar, pick a more distinct one or verify absence differently.
        // For this example, let's assume "Hindi" is not in learnableLanguages.
        // The default nativeLanguages list does contain Hindi. learnableLanguages does not.
        composeTestRule.onNodeWithText("Hindi").assertDoesNotExist()


        // "Next" button (FAB) should not exist initially
        composeTestRule.onNodeWithContentDescription("Next").assertDoesNotExist()

        // Select a language (e.g., German)
        composeTestRule.onNodeWithText("German", useUnmergedTree = true).performClick()

        // "Next" button (FAB) should now be displayed and enabled
        composeTestRule.onNodeWithContentDescription("Next").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Next").assertIsEnabled()
        composeTestRule.onNodeWithContentDescription("Next").performClick()

        // Verify callback
        verify(onLanguageSelectedMock).invoke("de") // Assuming "de" is the code for German
    }
}
