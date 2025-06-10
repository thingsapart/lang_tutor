package com.example.languageapp.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.languageapp.navigation.Screen
import com.example.languageapp.ui.screens.*

@Composable
fun AppNavigator() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.LanguageSelectorNative.route) {
        composable(Screen.LanguageSelectorNative.route) {
            LanguageSelectorScreen { nativeLanguageCode ->
                // For now, we're not using nativeLanguageCode, but it's selected.
                navController.navigate(Screen.LanguageSelectorLearn.route)
            }
        }

        composable(Screen.LanguageSelectorLearn.route) {
            // We can pass a parameter to LanguageSelectorScreen to change title/purpose if needed
            // For now, it's the same screen, but the action is different.
            LanguageSelectorScreen { languageCode ->
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
            // Pass languageCode and topicId to ChatScreen or use them to initialize chat
            ChatScreen(chatId = "topic_${languageCode}_${topicId}") // Example chatId generation
        }

        composable(
            route = Screen.ChatFromId.route,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId")
            ChatScreen(chatId = chatId)
        }

        composable(Screen.OngoingChats.route) {
            OngoingChatsScreen { chatId ->
                navController.navigate(Screen.ChatFromId.createRoute(chatId))
            }
        }

        composable(Screen.ReturningUser.route) {
            ReturningUserScreen(
                onContinueChatClicked = { navController.navigate(Screen.OngoingChats.route) },
                onSelectTopicClicked = { navController.navigate(Screen.LanguageSelectorLearn.route) } // Or a specific topic selection for returning user
            )
        }
    }
}
