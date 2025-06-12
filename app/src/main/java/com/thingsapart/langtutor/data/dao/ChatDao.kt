package com.thingsapart.langtutor.data.dao

import androidx.room.*
import com.thingsapart.langtutor.data.model.ChatConversationEntity
import com.thingsapart.langtutor.data.model.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ChatConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE) // Or OnConflictStrategy.ABORT if messages should be unique if re-inserted
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM conversations ORDER BY lastMessageTimestamp DESC")
    fun getAllConversations(): Flow<List<ChatConversationEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<ChatMessageEntity>>

    @Query("UPDATE conversations SET lastMessage = :lastMessage, lastMessageTimestamp = :timestamp WHERE id = :conversationId")
    suspend fun updateConversationSummary(conversationId: String, lastMessage: String, timestamp: Long)

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    fun getConversationById(conversationId: String): Flow<ChatConversationEntity?>

    @Query("SELECT * FROM conversations WHERE targetLanguageCode = :languageCode AND topicId = :topicId LIMIT 1")
    suspend fun getConversationByLanguageAndTopic(languageCode: String, topicId: String): ChatConversationEntity?

    // Optional: Example delete methods
    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: Long)
}
