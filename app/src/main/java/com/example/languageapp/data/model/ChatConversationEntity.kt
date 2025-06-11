package com.example.languageapp.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ChatConversationEntity(
    @PrimaryKey val id: String,
    val targetLanguageCode: String,
    val topicId: String?, // Can be null if it's a general chat or not topic-specific
    val lastMessage: String?,
    val lastMessageTimestamp: Long,
    val userProfileImageUrl: String?, // Could be an AI buddy avatar or a generic one
    val conversationTitle: String // e.g., "Spanish: Greetings" or "Practice Chat"
)
