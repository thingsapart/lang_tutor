package com.thingsapart.langtutor.data

import com.thingsapart.langtutor.data.dao.ChatDao
import com.thingsapart.langtutor.data.model.ChatConversationEntity
import com.thingsapart.langtutor.data.model.ChatMessageEntity
import com.thingsapart.langtutor.llm.LlmService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class ChatRepository(
    private val chatDao: ChatDao,
    private val llmService: LlmService
) {

    fun getAllConversations(): Flow<List<ChatConversationEntity>> = chatDao.getAllConversations()

    suspend fun findOrCreateConversationForTopic(languageCode: String, topicId: String): ChatConversationEntity {
        val existingConversation = chatDao.getConversationByLanguageAndTopic(languageCode, topicId)
        if (existingConversation != null) {
            return existingConversation
        }

        // No existing conversation, create a new one
        val newConversationId = java.util.UUID.randomUUID().toString()
        val conversation = ChatConversationEntity(
            id = newConversationId,
            targetLanguageCode = languageCode,
            topicId = topicId,
            lastMessage = "Conversation started.", // Initial placeholder
            lastMessageTimestamp = System.currentTimeMillis(),
            userProfileImageUrl = null, // Add appropriate URL if available
            conversationTitle = "$languageCode: $topicId" // Or generate a more descriptive title
        )

        chatDao.insertConversation(conversation) // Save conversation details first

        // Attempt to get initial AI greeting only if LLM is ready
        // The LlmServiceState check should ideally be done by the caller (ViewModel/Screen)
        // but for now, let's assume it's implicitly handled or we proceed carefully.
        // For a robust solution, LlmService readiness should be confirmed before calling this
        // or this method should indicate if AI greeting was added.

        var initialAiGreetingText: String
        try {
            // This call might be problematic if LLM isn't ready.
            // Consider making startNewConversation or parts of it callable from ChatScreen
            // based on LLM state, as it was partially doing.
            initialAiGreetingText = llmService.getInitialGreeting(
                topic = conversation.topicId ?: "general",
                targetLanguage = conversation.targetLanguageCode
            )
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Failed to get initial greeting from LLM for new chat: ${e.message}", e)
            initialAiGreetingText = "Welcome! Let's start our conversation about ${conversation.topicId ?: "this topic"}."
            // Save basic conversation even if LLM fails for greeting
            chatDao.updateConversationSummary(conversation.id, "Conversation created. AI greeting failed.", System.currentTimeMillis())
            return conversation // Return the conversation even if AI greeting fails
        }

        val initialAiMessage = ChatMessageEntity(
            conversationId = conversation.id,
            text = initialAiGreetingText,
            timestamp = System.currentTimeMillis(),
            isUserMessage = false
        )
        chatDao.insertMessage(initialAiMessage)
        chatDao.updateConversationSummary(conversation.id, initialAiMessage.text, initialAiMessage.timestamp)
        return conversation
    }

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
    // This method is now largely superseded by findOrCreateConversationForTopic's creation path.
    // It can be kept for cases where a conversation entity is pre-created and needs AI greeting.
    // Or it can be refactored/removed. For now, let's assume findOrCreate handles the main flow.
    suspend fun addInitialGreetingToConversation(conversation: ChatConversationEntity) {
        // This assumes conversation is already inserted.
        var initialAiGreetingText: String
        try {
            initialAiGreetingText = llmService.getInitialGreeting(
                topic = conversation.topicId ?: "general",
                targetLanguage = conversation.targetLanguageCode
            )
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Failed to get initial greeting from LLM: ${e.message}", e)
            initialAiGreetingText = "Welcome! Let's start our conversation about ${conversation.topicId ?: "this topic"}."
            // Update summary to reflect failure but continue
            chatDao.updateConversationSummary(conversation.id, "AI greeting failed.", System.currentTimeMillis())
            return // Exit if greeting fails
        }

        val initialAiMessage = ChatMessageEntity(
            conversationId = conversation.id,
            text = initialAiGreetingText,
            timestamp = System.currentTimeMillis(),
            isUserMessage = false
        )
        chatDao.insertMessage(initialAiMessage)
        chatDao.updateConversationSummary(conversation.id, initialAiMessage.text, initialAiMessage.timestamp)
    }

    @Deprecated("Use findOrCreateConversationForTopic and then addInitialGreetingToConversation if LLM is ready and greeting is desired separately.",
        ReplaceWith("this.findOrCreateConversationForTopic(conversation.targetLanguageCode, conversation.topicId ?: \"\")")
    )
    suspend fun startNewConversation(conversation: ChatConversationEntity) {
        val existing = chatDao.getConversationByLanguageAndTopic(conversation.targetLanguageCode, conversation.topicId ?: "")
        if (existing == null) {
            chatDao.insertConversation(conversation)
            addInitialGreetingToConversation(conversation)
        } else {
            // Handle case where conversation surprisingly already exists, maybe log or update
            android.util.Log.w("ChatRepository", "startNewConversation called for already existing topic pair.")
        }
    }
}
