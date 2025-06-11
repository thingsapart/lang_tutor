package com.example.languageapp.llm

import kotlinx.coroutines.flow.Flow

/**
 * Interface for interacting with a Language Model.
 */
interface LlmService {
    /**
     * Generates a response from the LLM based on the given prompt and conversation history.
     *
     * @param prompt The user's latest message or query.
     * @param conversationId The ID of the current conversation, which can be used to fetch history if needed.
     * @param targetLanguage The language in which the response should be generated.
     * @return A Flow that emits the LLM's response, potentially in chunks for streaming.
     */
    fun generateResponse(prompt: String, conversationId: String, targetLanguage: String): Flow<String>

    /**
     * Gets an initial greeting or starting message from the LLM for a new chat.
     *
     * @param topic The topic selected for the new chat.
     * @param targetLanguage The language for the greeting.
     * @return A string containing the initial message from the LLM.
     */
    suspend fun getInitialGreeting(topic: String, targetLanguage: String): String
}
