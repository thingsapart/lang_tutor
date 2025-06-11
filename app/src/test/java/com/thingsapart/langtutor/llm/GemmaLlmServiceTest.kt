package com.thingsapart.langtutor.llm

import android.content.Context
import com.example.languageapp.llm.GemmaLlmService // Actual class location
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals

@ExperimentalCoroutinesApi
class GemmaLlmServiceTest {

    private lateinit var context: Context
    private lateinit var gemmaLlmService: GemmaLlmService

    @Before
    fun setUp() {
        context = mock() // Mock Android Context
        // Corrected GemmaLlmService instantiation based on actual project structure
        gemmaLlmService = GemmaLlmService(context, "test-model-path.gguf")
    }

    @Test
    fun `generateResponse returns placeholder response with prompt and language`() = runTest {
        val prompt = "User query"
        val conversationId = "conv123"
        val targetLanguage = "fr"

        val responseFlow = gemmaLlmService.generateResponse(prompt, conversationId, targetLanguage)
        val response = responseFlow.first() // Collect the first emitted value

        // Check if the placeholder response contains the key elements
        assertTrue("Response should contain the original prompt", response.contains(prompt))
        assertTrue("Response should contain the target language", response.contains(targetLanguage))
        assertTrue("Response should indicate it's from Gemma", response.contains("Gemma's response"))
    }

    @Test
    fun `getInitialGreeting returns placeholder greeting with topic and language`() = runTest {
        val topic = "travel"
        val targetLanguage = "de"

        val greeting = gemmaLlmService.getInitialGreeting(topic, targetLanguage)

        // Check if the placeholder greeting contains the key elements
        assertTrue("Greeting should contain the topic", greeting.contains(topic))
        assertTrue("Greeting should contain the target language", greeting.contains(targetLanguage))
        assertTrue("Greeting should indicate it's from Gemma", greeting.contains("Gemma"))
        assertEquals(
            "Hello! Let's talk about $topic in $targetLanguage. I am Gemma, your AI assistant.",
            greeting
        )
    }
}
