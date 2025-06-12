package com.thingsapart.langtutor.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.thingsapart.langtutor.ui.theme.LanguageAppTheme
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class TopicSelectorScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun captionDisplayed_letsStartButtonDisabledInitially_thenEnabled() {
        val onTopicSelectedMock = mock<(String) -> Unit>()

        composeTestRule.setContent {
            LanguageAppTheme {
                TopicSelectorScreen(onTopicSelected = onTopicSelectedMock)
            }
        }

        // Check caption
        composeTestRule.onNodeWithText("What do you want to talk about?").assertIsDisplayed()
        // Check TopAppBar title
        composeTestRule.onNodeWithText("Choose a Topic").assertIsDisplayed()

        // "Let's Start" button (FAB) should not exist or be disabled initially
        // As it's a FAB that appears, assertDoesNotExist is more accurate for initial state
        composeTestRule.onNodeWithText("Let's Start").assertDoesNotExist() // The text is inside the FAB's Row

        // Select a topic (e.g., "Ordering Food", assuming its id is "ordering_food")
        // This requires TopicCard to be identifiable.
        composeTestRule.onNodeWithText("Ordering Food", useUnmergedTree = true).performClick()

        // "Let's Start" button (FAB) should now be displayed and enabled
        // The FAB itself contains the text "Let's Start" and an Icon.
        // We can find the FAB by its content description if set, or by the text.
        val letsStartButton = composeTestRule.onNodeWithText("Let's Start")
        letsStartButton.assertIsDisplayed()
        letsStartButton.assertIsEnabled() // Check if the clickable node is enabled

        // Perform click on the node that contains the text "Let's Start"
        // This might be the Row inside the FAB. If the FAB itself is the clickable node, adjust accordingly.
        letsStartButton.performClick()


        // Verify that the callback was called with the correct topic ID
        verify(onTopicSelectedMock).invoke("ordering_food")
    }

    @Test
    fun topicList_isDisplayed() {
        composeTestRule.setContent {
            LanguageAppTheme {
                TopicSelectorScreen(onTopicSelected = {})
            }
        }

        // Check for some topics from the engagingTopics list
        composeTestRule.onNodeWithText("Ordering Food").assertIsDisplayed()
        composeTestRule.onNodeWithText("Talking Hobbies").assertIsDisplayed()
        composeTestRule.onNodeWithText("Travel Plans").assertIsDisplayed()
    }
}
