package com.thingsapart.langtutor.ui

import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.thingsapart.langtutor.data.UserSettingsRepository
import com.thingsapart.langtutor.llm.LlmService
import com.thingsapart.langtutor.navigation.Screen
import com.thingsapart.langtutor.ui.screens.ChatScreen
import com.thingsapart.langtutor.ui.screens.Language // Import Language data class
import com.thingsapart.langtutor.ui.screens.LanguageSelectorScreen
import com.thingsapart.langtutor.ui.screens.OngoingChatsScreen
import com.thingsapart.langtutor.ui.screens.ReturningUserScreen
import com.thingsapart.langtutor.ui.screens.TopicSelectorScreen
import com.thingsapart.langtutor.ui.screens.WelcomeScreen // Added import
import kotlinx.coroutines.launch

@Composable
fun AppNavigator(
    userSettingsRepository: UserSettingsRepository,
    chatRepository: com.thingsapart.langtutor.data.ChatRepository, // Added ChatRepository
    llmService: LlmService // Added
) {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()

    // Observe native language setting
    val nativeLanguage by userSettingsRepository.nativeLanguage.collectAsState(initial = null)

    // Determine start destination based on whether native language is set
    // This logic will run once when AppNavigator is first composed.
    // If nativeLanguage flow emits a new value, this NavHost might recompose
    // but the startDestination is fixed after initial setup.
    // A more complex setup might involve a loading screen or observing this value
    // outside NavHost to conditionally render different NavHost graphs or redirect.
    // For now, we'll keep it simple; this primarily affects the *initial* screen.
    // The WelcomeScreen is now the universal start destination.
    val startDestination = Screen.Welcome.route

    // Show a loading indicator or an empty screen while determining start destination
    // This is a simplified approach. A real app might use a splash screen or a loading composable.
    // This loading check might need to be re-evaluated or moved if WelcomeScreen handles it.
    if (nativeLanguage === null && startDestination == Screen.ReturningUser.route) { // This condition will likely not be met anymore with Welcome as start
         // Still loading the preference, or it's null and we decided to go to ReturningUser (which shouldn't happen with current logic)
         // To avoid flicker or incorrect initial screen, one might show a loading screen here.
         // For this iteration, we'll let it proceed, which might briefly show native selector if preference loads slow.
    }


    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.Welcome.route) {
            WelcomeScreen(onNextClicked = {
                val destination = if (nativeLanguage != null) {
                    Screen.ReturningUser.route
                } else {
                    Screen.LanguageSelectorNative.route
                }
                navController.navigate(destination) {
                    // Optional: Pop WelcomeScreen from back stack if you don't want users to return to it
                    popUpTo(Screen.Welcome.route) { inclusive = true }
                }
            })
        }

        composable(Screen.LanguageSelectorNative.route) {
            val nativeLanguages = listOf(
                Language("English", "en", "https://flagcdn.com/w320/us.png"),
                Language("German", "de", "https://flagcdn.com/w320/de.png"),
                Language("Spanish", "es", "https://flagcdn.com/w320/es.png")
            )
            LanguageSelectorScreen(
                title = "Native Language",
                caption = "Select your native language",
                languages = nativeLanguages,
                onLanguageSelected = { nativeLanguageCode ->
                    coroutineScope.launch {
                        userSettingsRepository.saveNativeLanguage(nativeLanguageCode)
                    }
                    // After saving, the startDestination logic would ideally pick up the change on next app launch.
                    // For immediate navigation reflecting the "first time flow":
                    navController.navigate(Screen.LanguageSelectorLearn.route) {
                        // Pop LanguageSelectorNative so user can't go back to it after selection
                        popUpTo(Screen.LanguageSelectorNative.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.LanguageSelectorLearn.route) {
            // Define the list of languages to learn
            val learnLanguages = listOf(
                Language("English", "en", "https://flagcdn.com/w320/gb.png"),
                Language("German", "de", "https://flagcdn.com/w320/de.png"),
                Language("Spanish", "es", "https://flagcdn.com/w320/es.png"),
                Language("Japanese", "ja", "https://flagcdn.com/w320/jp.png"),
                Language("Chinese", "zh", "https://flagcdn.com/w320/cn.png"),
                Language("Korean", "ko", "https://flagcdn.com/w320/kr.png"),
                Language("Norwegian", "no", "https://flagcdn.com/w320/no.png"),
                Language("Swedish", "sv", "https://flagcdn.com/w320/se.png")
            )
            LanguageSelectorScreen(
                title = "Language to Learn", // This title is appropriate
                caption = "Now select the language you want to learn", // Updated caption
                languages = learnLanguages, // Updated list
                onLanguageSelected = { languageCode ->
                    // Ensure this navigation is correct
                    navController.navigate(Screen.TopicSelector.createRoute(languageCode))
                }
            )
        }

        composable(
            route = Screen.TopicSelector.route,
            arguments = listOf(navArgument("languageCode") { type = NavType.StringType })
        ) { backStackEntry ->
            val languageCode = backStackEntry.arguments?.getString("languageCode")
            TopicSelectorScreen { topicId ->
                navController.navigate(Screen.ChatFromTopic.createRoute(languageCode ?: "unknown", topicId))
            }
        }

        composable(
            route = Screen.ChatFromTopic.route,
            arguments = listOf(
                navArgument("languageCode") { type = NavType.StringType },
                navArgument("topicId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val languageCode = backStackEntry.arguments?.getString("languageCode")
            val topicId = backStackEntry.arguments?.getString("topicId")
            ChatScreen(
                chatId = null, // Explicitly null for new chat from topic
                languageCode = languageCode,
                topicId = topicId,
                chatRepository = chatRepository,
                userSettingsRepository = userSettingsRepository,
                llmService = llmService // Added
            )
        }

        composable(
            route = Screen.ChatFromId.route,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId")
            ChatScreen(
                chatId = chatId,
                chatRepository = chatRepository,
                userSettingsRepository = userSettingsRepository, // Assuming ChatScreen might need this too
                llmService = llmService // Added
            )
        }

        composable(Screen.OngoingChats.route) {
            // Pass chatRepository to OngoingChatsScreen
            OngoingChatsScreen(chatRepository = chatRepository) { chatId ->
                navController.navigate(Screen.ChatFromId.createRoute(chatId))
            }
        }

        composable(Screen.ReturningUser.route) {
            ReturningUserScreen(
                onContinueChatClicked = { navController.navigate(Screen.OngoingChats.route) },
                onSelectTopicClicked = {
                    // For a returning user, LanguageSelectorLearn might be more appropriate
                    // if they want to learn a *new* language or re-select.
                    // Or, it could go to TopicSelector for their *current* learning language.
                    // This depends on the desired UX. For now, let's go to LanguageSelectorLearn.
                    navController.navigate(Screen.LanguageSelectorLearn.route)
                }
            )
        }
    }
}
