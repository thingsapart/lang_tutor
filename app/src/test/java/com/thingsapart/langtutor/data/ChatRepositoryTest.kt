package com.thingsapart.langtutor.data

import com.example.languageapp.data.ChatRepository // Actual class location
import com.example.languageapp.data.dao.ChatDao
import com.example.languageapp.data.model.ChatConversationEntity
import com.example.languageapp.data.model.ChatMessageEntity
import com.example.languageapp.llm.LlmService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest // Using runTest for coroutine testing
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.kotlin.times

@ExperimentalCoroutinesApi
class ChatRepositoryTest {

    private lateinit var chatDao: ChatDao
    private lateinit var llmService: LlmService
    private lateinit var chatRepository: ChatRepository

    private val testConversationId = "testConvId"
    private val testTargetLanguage = "es"
    private val testTopic = "greetings"

    @Before
    fun setUp() {
        chatDao = mock()
        llmService = mock()
        // Corrected ChatRepository instantiation based on actual project structure
        chatRepository = ChatRepository(chatDao, llmService)
    }

    @Test
    fun `sendMessage saves user message, gets AI response, and saves AI message`() = runTest {
        val userMessageText = "Hello AI"
        val aiResponseText = "Hello User"
        val userMessage = ChatMessageEntity(
            conversationId = testConversationId,
            text = userMessageText,
            timestamp = System.currentTimeMillis(),
            isUserMessage = true
        )
        // Mock DAO to return a conversation so targetLanguage can be determined
        val mockConversation = ChatConversationEntity(
            id = testConversationId,
            targetLanguageCode = testTargetLanguage,
            topicId = testTopic,
            lastMessage = null,
            lastMessageTimestamp = 0L,
            conversationTitle = "Test Chat"
        )
        // Use whenever for stubbing suspend functions or Flow
        whenever(chatDao.getConversationById(testConversationId)).thenReturn(flowOf(mockConversation))
        whenever(llmService.generateResponse(userMessageText, testConversationId, testTargetLanguage))
            .thenReturn(flowOf(aiResponseText))

        chatRepository.sendMessage(testConversationId, userMessage)

        // Verify user message was saved
        verify(chatDao).insertMessage(userMessage)
        verify(chatDao).updateConversationSummary(testConversationId, userMessageText, userMessage.timestamp)

        // Verify LLM service was called
        verify(llmService).generateResponse(userMessageText, testConversationId, testTargetLanguage)

        // Verify AI message was saved (any ChatMessageEntity where isUserMessage is false)
        verify(chatDao).insertMessage(any<ChatMessageEntity> { !it.isUserMessage && it.text == aiResponseText })
        verify(chatDao, times(2)).updateConversationSummary(any(), any(), any()) // Once for user, once for AI
    }

    @Test
    fun `startNewConversation saves conversation, gets initial AI greeting, and saves it`() = runTest {
        val initialGreetingText = "Welcome! Let's talk about $testTopic."
        val conversation = ChatConversationEntity(
            id = testConversationId,
            targetLanguageCode = testTargetLanguage,
            topicId = testTopic,
            lastMessage = null,
            lastMessageTimestamp = System.currentTimeMillis(),
            conversationTitle = "New Chat about $testTopic"
        )

        whenever(llmService.getInitialGreeting(testTopic, testTargetLanguage))
            .thenReturn(initialGreetingText)

        chatRepository.startNewConversation(conversation)

        // Verify conversation was saved
        verify(chatDao).insertConversation(conversation)

        // Verify LLM service was called for initial greeting
        verify(llmService).getInitialGreeting(testTopic, testTargetLanguage)

        // Verify initial AI message was saved
        val expectedAiMessage = any<ChatMessageEntity> {
            !it.isUserMessage && it.text == initialGreetingText && it.conversationId == testConversationId
        }
        verify(chatDao).insertMessage(expectedAiMessage)
        verify(chatDao).updateConversationSummary(testConversationId, initialGreetingText, any())
    }
}
