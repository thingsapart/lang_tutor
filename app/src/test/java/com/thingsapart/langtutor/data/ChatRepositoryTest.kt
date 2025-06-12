package com.thingsapart.langtutor.data

import com.example.languageapp.data.dao.ChatDao
import com.example.languageapp.data.model.ChatConversationEntity
import com.example.languageapp.data.model.ChatMessageEntity
import com.example.languageapp.llm.LlmService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.mockito.kotlin.* // Ensure all mockito-kotlin functions are available
import java.io.IOException // For testing exception case


@ExperimentalCoroutinesApi
class ChatRepositoryTest {

    private lateinit var chatDao: ChatDao
    private lateinit var llmService: LlmService
    private lateinit var chatRepository: com.thingsapart.langtutor.data.ChatRepository

    private val testConversationId = "testConvId"
    private val testTargetLanguage = "es"
    private val testTopic = "greetings"

    @Before
    fun setUp() {
        chatDao = mock()
        llmService = mock()
        chatRepository = com.thingsapart.langtutor.data.ChatRepository(chatDao, llmService)
    }

    @Test
    fun `sendMessage saves user message, gets AI response, and saves AI message`() = runTest {
        val userMessageText = "Hello AI"
        val aiResponseText = "Hello User" // This comes from LlmService, not LlmInferenceSession directly in this test
        val userMessage = ChatMessageEntity(
            conversationId = testConversationId,
            text = userMessageText,
            timestamp = System.currentTimeMillis(),
            isUserMessage = true
        )
        val mockConversation = ChatConversationEntity(
            id = testConversationId,
            targetLanguageCode = testTargetLanguage,
            topicId = testTopic,
            lastMessage = null,
            lastMessageTimestamp = 0L,
            conversationTitle = "Test Chat"
        )
        whenever(chatDao.getConversationById(testConversationId)).thenReturn(flowOf(mockConversation))
        // Assuming generateResponse from LlmService interface directly returns the string flow
        whenever(llmService.generateResponse(userMessageText, testConversationId, testTargetLanguage))
            .thenReturn(flowOf(aiResponseText))

        chatRepository.sendMessage(testConversationId, userMessage)

        verify(chatDao).insertMessage(userMessage)
        verify(chatDao).updateConversationSummary(testConversationId, userMessageText, userMessage.timestamp)
        verify(llmService).generateResponse(userMessageText, testConversationId, testTargetLanguage)
        verify(chatDao).insertMessage(argThat<ChatMessageEntity> { !it.isUserMessage && it.text == aiResponseText })
        verify(chatDao, times(2)).updateConversationSummary(any(), any(), any())
    }

    @Test
    fun `startNewConversation saves conversation, gets initial AI greeting successfully, and saves it`() = runTest {
        val successfulGreetingText = "Welcome! Let's talk about $testTopic in $testTargetLanguage."
        val conversation = ChatConversationEntity(
            id = testConversationId,
            targetLanguageCode = testTargetLanguage,
            topicId = testTopic,
            lastMessage = null, // Will be updated
            lastMessageTimestamp = System.currentTimeMillis(), // Will be updated
            conversationTitle = "New Chat about $testTopic"
        )

        whenever(llmService.getInitialGreeting(eq(testTopic), eq(testTargetLanguage)))
            .thenReturn(successfulGreetingText)

        chatRepository.startNewConversation(conversation)

        verify(chatDao).insertConversation(conversation)
        verify(llmService).getInitialGreeting(testTopic, testTargetLanguage)

        val messageCaptor = argumentCaptor<ChatMessageEntity>()
        verify(chatDao).insertMessage(messageCaptor.capture())
        verify(chatDao).updateConversationSummary(eq(testConversationId), eq(successfulGreetingText), any())

        val capturedMessage = messageCaptor.firstValue
        assertEquals(testConversationId, capturedMessage.conversationId)
        assertEquals(successfulGreetingText, capturedMessage.text)
        assertFalse(capturedMessage.isUserMessage)
    }

    @Test
    fun `startNewConversation when getInitialGreeting fails, uses default greeting`() = runTest {
        val conversation = ChatConversationEntity(
            id = testConversationId,
            targetLanguageCode = testTargetLanguage,
            topicId = testTopic,
            lastMessage = null,
            lastMessageTimestamp = System.currentTimeMillis(),
            conversationTitle = "New Chat about $testTopic"
        )
        val expectedDefaultGreeting = "Welcome! Let's start our conversation about $testTopic."

        // Simulate getInitialGreeting throwing an exception
        whenever(llmService.getInitialGreeting(eq(testTopic), eq(testTargetLanguage)))
            .thenThrow(IOException("LLM service unavailable"))

        chatRepository.startNewConversation(conversation)

        verify(chatDao).insertConversation(conversation)
        verify(llmService).getInitialGreeting(testTopic, testTargetLanguage) // Still called

        val messageCaptor = argumentCaptor<ChatMessageEntity>()
        verify(chatDao).insertMessage(messageCaptor.capture()) // Verify insertMessage is still called
        verify(chatDao).updateConversationSummary(eq(testConversationId), eq(expectedDefaultGreeting), any())

        val capturedMessage = messageCaptor.firstValue
        assertEquals(testConversationId, capturedMessage.conversationId)
        assertEquals(expectedDefaultGreeting, capturedMessage.text)
        assertFalse(capturedMessage.isUserMessage)
    }

    @Test
    fun `getConversationByLanguageAndTopic returns conversation when DAO finds one`() = runTest {
        val languageCode = "es"
        val topicId = "greetings"
        val expectedConversation = ChatConversationEntity(
            id = "1",
            targetLanguageCode = languageCode,
            topicId = topicId,
            lastMessage = "Hola",
            lastMessageTimestamp = System.currentTimeMillis(),
            userProfileImageUrl = null,
            conversationTitle = "Spanish Greetings"
        )

        whenever(chatDao.getConversationByLanguageAndTopic(languageCode, topicId))
            .thenReturn(flowOf(expectedConversation))

        val result = chatRepository.getConversationByLanguageAndTopic(languageCode, topicId).first()

        assertNotNull(result)
        assertEquals(expectedConversation.id, result?.id)
        assertEquals(languageCode, result?.targetLanguageCode)
        assertEquals(topicId, result?.topicId)
    }

    @Test
    fun `getConversationByLanguageAndTopic returns null when DAO finds none`() = runTest {
        val languageCode = "fr"
        val topicId = "travel"

        whenever(chatDao.getConversationByLanguageAndTopic(languageCode, topicId))
            .thenReturn(flowOf(null)) // DAO returns flow of null

        val result = chatRepository.getConversationByLanguageAndTopic(languageCode, topicId).first()

        assertNull(result)
    }
}
