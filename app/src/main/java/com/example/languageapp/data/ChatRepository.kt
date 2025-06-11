package com.example.languageapp.data

import com.example.languageapp.data.dao.ChatDao
import com.example.languageapp.data.model.ChatConversationEntity
import com.example.languageapp.data.model.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatDao: ChatDao) {

    fun getAllConversations(): Flow<List<ChatConversationEntity>> = chatDao.getAllConversations()

    fun getMessagesForConversation(conversationId: String): Flow<List<ChatMessageEntity>> =
        chatDao.getMessagesForConversation(conversationId)

    /**
     * Sends a message and updates the conversation summary.
     * If conversationDetails are provided, it means this might be the first message of a new conversation.
     * The conversation entity should be inserted or updated accordingly.
     */
    suspend fun sendMessage(
        conversationId: String,
        message: ChatMessageEntity,
        conversationDetails: ChatConversationEntity? = null
    ) {
        // If this is a new conversation (details provided), ensure the conversation exists.
        // If it's an existing conversation, these details might be used to update title/image if needed,
        // but primarily we just need to ensure it exists before adding a message.
        // The OnConflictStrategy.REPLACE in ChatDao for insertConversation handles updates.
        if (conversationDetails != null) {
            chatDao.insertConversation(conversationDetails)
        }

        chatDao.insertMessage(message)
        chatDao.updateConversationSummary(conversationId, message.text, message.timestamp)
    }

    /**
     * Starts a new conversation by inserting its details.
     * This is useful when a conversation is created (e.g., by selecting a language/topic)
     * before any messages are sent.
     */
    suspend fun startNewConversation(conversation: ChatConversationEntity) {
        chatDao.insertConversation(conversation)
    }

    // Potentially add other methods like:
    // suspend fun getConversationDetails(conversationId: String): ChatConversationEntity?
    // suspend fun createNewChatId(...): String
}
