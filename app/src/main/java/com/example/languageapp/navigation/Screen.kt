package com.example.languageapp.navigation

sealed class Screen(val route: String) {
    object LanguageSelectorNative : Screen("language_selector_native")
    object LanguageSelectorLearn : Screen("language_selector_learn")
    object TopicSelector : Screen("topic_selector/{languageCode}") {
        fun createRoute(languageCode: String) = "topic_selector/$languageCode"
    }
    object ChatFromTopic : Screen("chat_from_topic/{languageCode}/{topicId}") {
        fun createRoute(languageCode: String, topicId: String) = "chat_from_topic/$languageCode/$topicId"
    }
    object ChatFromId : Screen("chat_from_id/{chatId}") {
        fun createRoute(chatId: String) = "chat_from_id/$chatId"
    }
    object OngoingChats : Screen("ongoing_chats")
    object ReturningUser : Screen("returning_user")

    // Helper to add arguments to routes easily
    fun withArgs(vararg args: String): String {
        return buildString {
            append(route)
            args.forEach { arg ->
                append("/$arg")
            }
        }
    }
}
