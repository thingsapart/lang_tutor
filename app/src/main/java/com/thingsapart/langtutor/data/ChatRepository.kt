package com.thingsapart.langtutor.data

import com.thingsapart.langtutor.data.dao.ChatDao
import com.thingsapart.langtutor.data.model.ChatConversationEntity
import com.thingsapart.langtutor.data.model.ChatMessageEntity
import com.thingsapart.langtutor.llm.LlmService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class ChatRepository(
    private val chatDao: ChatDao,
    private val llmService: LlmService // Added LlmService dependency
) {

    fun getAllConversations(): Flow<List<ChatConversationEntity>> = chatDao.getAllConversations()

    fun getMessagesForConversation(conversationId: String): Flow<List<ChatMessageEntity>> =
        chatDao.getMessagesForConversation(conversationId)

    /**
     * Sends a user message, saves it, then gets and saves the AI's response.
     * Updates the conversation summary.
     */
    suspend fun sendMessage(
        conversationId: String,
        userMessage: ChatMessageEntity,
        // Removed conversationDetails: ChatConversationEntity? = null - this will be handled by startNewConversation
    ) {
        // Save the user's message first
        chatDao.insertMessage(userMessage)
        chatDao.updateConversationSummary(conversationId, userMessage.text, userMessage.timestamp)

        // Get AI response
        // Assuming targetLanguage is stored in the conversation entity or can be fetched
        val conversation = chatDao.getConversationById(conversationId).firstOrNull() // Helper needed in DAO
        val targetLanguage = conversation?.targetLanguageCode ?: "en" // Default or fetch appropriately

        llmService.generateResponse(userMessage.text, conversationId, targetLanguage)
            .collect { aiText -> // Assuming flow emits one complete response for now
                val aiMessage = ChatMessageEntity(
                    conversationId = conversationId,
                    text = aiText,
                    timestamp = System.currentTimeMillis(),
                    isUserMessage = false
                )
                chatDao.insertMessage(aiMessage)
                chatDao.updateConversationSummary(conversationId, aiMessage.text, aiMessage.timestamp)
            }
    }

    /**
     * Starts a new conversation by inserting its details and an initial AI greeting.
     */
    suspend fun startNewConversation(conversation: ChatConversationEntity) {
        chatDao.insertConversation(conversation) // Save conversation details first

        var initialAiGreetingText: String
        try {
            initialAiGreetingText = llmService.getInitialGreeting(
                topic = conversation.topicId ?: "general",
                targetLanguage = conversation.targetLanguageCode
            )
        } catch (e: Exception) {
            // Log the exception for debugging
            // android.util.Log.e("ChatRepository", "Failed to get initial greeting from LLM: ${e.message}", e)
            // Provide a default greeting
            initialAiGreetingText = "Welcome! Let's start our conversation about ${conversation.topicId ?: "this topic"}."
        }

        val initialAiMessage = ChatMessageEntity(
            conversationId = conversation.id,
            text = initialAiGreetingText,
            timestamp = System.currentTimeMillis(), // Ensure this is slightly after conversation creation if needed
            isUserMessage = false
        )
        chatDao.insertMessage(initialAiMessage)
        // Update conversation summary with the AI's first message
        chatDao.updateConversationSummary(conversation.id, initialAiMessage.text, initialAiMessage.timestamp)
    }

    // Note: A new method getConversationById(conversationId: String): Flow<ChatConversationEntity?>
    // will be needed in ChatDao and subsequently here if not already present.
    // For simplicity in this step, we'll assume it can be added or worked around.
    // If ChatDao.getConversationById is suspend fun, then .firstOrNull() is not needed.

    fun getConversationById(conversationId: String): Flow<ChatConversationEntity?> {
        return chatDao.getConversationById(conversationId)
    }

    fun getConversationByLanguageAndTopic(languageCode: String, topicId: String): Flow<ChatConversationEntity?> {
        return chatDao.getConversationByLanguageAndTopic(languageCode, topicId)
    }
}
