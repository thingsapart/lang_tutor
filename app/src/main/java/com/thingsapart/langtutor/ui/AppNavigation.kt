package com.thingsapart.langtutor.ui

import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.languageapp.data.UserSettingsRepository
import com.example.languageapp.llm.MediaPipeLlmService // Added
import com.example.languageapp.navigation.Screen
import com.example.languageapp.ui.screens.*
import kotlinx.coroutines.launch

@Composable
fun AppNavigator(
    userSettingsRepository: UserSettingsRepository,
    chatRepository: com.thingsapart.langtutor.data.ChatRepository, // Added ChatRepository
    llmService: MediaPipeLlmService // Added
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
    val startDestination = remember(nativeLanguage) { // Recalculate only if nativeLanguage changes *before* NavHost shown
        if (nativeLanguage != null) {
            Screen.ReturningUser.route
        } else {
            Screen.LanguageSelectorNative.route
        }
    }

    // Show a loading indicator or an empty screen while determining start destination
    // This is a simplified approach. A real app might use a splash screen or a loading composable.
    if (nativeLanguage === null && startDestination == Screen.ReturningUser.route) {
         // Still loading the preference, or it's null and we decided to go to ReturningUser (which shouldn't happen with current logic)
         // To avoid flicker or incorrect initial screen, one might show a loading screen here.
         // For this iteration, we'll let it proceed, which might briefly show native selector if preference loads slow.
    }


    NavHost(navController = navController, startDestination = startDestination) {
        composable(Screen.LanguageSelectorNative.route) {
            LanguageSelectorScreen { nativeLanguageCode ->
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
        }

        composable(Screen.LanguageSelectorLearn.route) {
            LanguageSelectorScreen { languageCode ->
                // Here, languageCode is the language to learn
                navController.navigate(Screen.TopicSelector.createRoute(languageCode))
            }
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
