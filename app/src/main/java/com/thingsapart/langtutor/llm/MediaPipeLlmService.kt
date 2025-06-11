package com.thingsapart.langtutor.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
// Removed: import kotlinx.coroutines.flow.flow
import java.io.File

// Sealed interface LlmServiceState (assuming it's in the same package or imported)
// No change to LlmServiceState itself in this step.

class MediaPipeLlmService(
    private val context: Context,
    private val modelConfig: LlmModelConfig = ModelManager.DEFAULT_MODEL,
    private val modelDownloader: ModelDownloader
) : LlmService { // Implements LlmService
    private val _serviceState = MutableStateFlow<LlmServiceState>(LlmServiceState.Idle)
    override val serviceState: StateFlow<LlmServiceState> = _serviceState.asStateFlow()

    private var llmInference: LlmInference? = null
    private var llmSession: LlmInferenceSession? = null // Added session member

    companion object {
        private const val TAG = "MediaPipeLlmService"
    }

    // Updated: Add override
    override suspend fun initialize() {
        Log.i(TAG, "initialize called. Current state: ${_serviceState.value}")
        // Close existing session and engine before re-initializing
        close() // Ensures a clean slate by calling our enhanced close()

        _serviceState.value = LlmServiceState.Initializing
        Log.i(TAG, "Initializing for model: ${modelConfig.modelName}")

        val modelFile: File
        if (!ModelManager.checkModelExists(context, modelConfig)) {
            Log.i(TAG, "Model ${modelConfig.modelName} not found locally. Starting download from ${modelConfig.url}")
            _serviceState.value = LlmServiceState.Downloading(modelConfig, 0f)
            val downloadResult = modelDownloader.downloadModel(context, modelConfig) { progress ->
                val currentState = _serviceState.value
                if (currentState is LlmServiceState.Downloading && currentState.model.internalModelId == modelConfig.internalModelId) {
                    _serviceState.value = LlmServiceState.Downloading(modelConfig, progress)
                }
            }

            if (downloadResult.isFailure) {
                val errorMsg = "Failed to download model ${modelConfig.modelName}: ${downloadResult.exceptionOrNull()?.message}"
                Log.e(TAG, errorMsg, downloadResult.exceptionOrNull())
                _serviceState.value = LlmServiceState.Error(errorMsg, modelConfig)
                return
            }
            modelFile = downloadResult.getOrThrow()
            Log.i(TAG, "Model ${modelConfig.modelName} downloaded successfully to ${modelFile.absolutePath}")
        } else {
            modelFile = ModelManager.getLocalModelFile(context, modelConfig)
            Log.i(TAG, "Model ${modelConfig.modelName} found locally at ${modelFile.absolutePath}")
        }

        try {
            Log.i(TAG, "Creating LlmInference engine for ${modelConfig.modelName} at ${modelFile.absolutePath}")
            val optionsBuilder = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)

            ModelManager.mapToMediaPipeBackend(modelConfig.preferredBackend)?.let {
                optionsBuilder.setPreferredBackend(it)
                Log.i(TAG, "Set preferred backend to: $it")
            }

            val options = optionsBuilder
                .setPreferredBackend(LlmInference.Backend.GPU)
                .setMaxTopK(modelConfig.topK)
                .setMaxTokens(modelConfig.maxTokens)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            Log.i(TAG, "LlmInference engine created. Creating session...")

            // Create and configure LlmInferenceSession
            llmSession = llmInference?.createSession()
            // Apply session-specific configurations
            llmSession?.configure(
                temperature = modelConfig.temperature,
                topK = modelConfig.topK,
                topP = modelConfig.topP,
                maxTokens = modelConfig.maxTokens
            )
            Log.i(TAG, "LlmInferenceSession created and configured for ${modelConfig.modelName}.")
            _serviceState.value = LlmServiceState.Ready
            Log.i(TAG, "MediaPipe LlmService initialized successfully and is Ready.")

        } catch (e: Exception) {
            val errorMsg = "Failed to initialize MediaPipe LLM engine or session for ${modelConfig.modelName}: ${e.message}"
            Log.e(TAG, errorMsg, e)
            _serviceState.value = LlmServiceState.Error(errorMsg, modelConfig)
            close() // Clean up engine if session creation failed
        }
    }

    // Updated: Add override and implement with LlmInferenceSession
    override fun generateResponse(prompt: String, conversationId: String, targetLanguage: String): Flow<String> {
        if (_serviceState.value !is LlmServiceState.Ready || llmSession == null) {
            val errorMsg = "LlmService is not ready or session is null. Current state: ${_serviceState.value}"
            Log.w(TAG, "generateResponse called when not ready: $errorMsg")
            return callbackFlow { throw IllegalStateException(errorMsg) } // callbackFlow requires throw, not just emit
        }

        // TODO: Incorporate conversationId and targetLanguage into the prompt or session management
        // For now, targetLanguage is informational. ConversationId might be used for future context window.
        val fullPrompt = "User: $prompt\nAI:" // Simple prompt, refine as needed
        Log.d(TAG, "Generating response for prompt: \"$fullPrompt\" with model ${modelConfig.modelName}")

        return callbackFlow {
            val progressListener = ProgressListener { partialResult, done ->
                Log.v(TAG, "Partial result: $partialResult, Done: $done")
                trySend(partialResult)
                if (done) {
                    channel.close()
                    Log.d(TAG, "Response generation completed and channel closed.")
                }
            }

            try {
                llmSession?.generateResponseAsync(fullPrompt, progressListener)
            } catch (e: Exception) {
                Log.e(TAG, "Exception during generateResponseAsync: ${e.message}", e)
                channel.close(e) // Close channel with exception
            }

            awaitClose { Log.d(TAG, "callbackFlow awaitClose for generateResponse.") }
        }
    }

    // Updated: Add override and implement using the new generateResponse
    override suspend fun getInitialGreeting(topic: String, targetLanguage: String): String {
         if (_serviceState.value !is LlmServiceState.Ready || llmSession == null) {
            val errorMsg = "LlmService is not ready or session is null when getting initial greeting. State: ${_serviceState.value}"
            Log.w(TAG, errorMsg)
            // Fallback or throw. For now, a simple fallback.
            return "Hello! I'm currently unable to generate a full greeting. Let's talk about $topic."
        }
        // Construct a prompt for a greeting. This can be more sophisticated.
        val greetingPrompt = "Generate a friendly, engaging opening message for a conversation about '$topic' in $targetLanguage."
        Log.i(TAG, "Requesting initial greeting for topic: $topic, language: $targetLanguage")

        val stringBuilder = StringBuilder()
        try {
            generateResponse(greetingPrompt, "initialGreeting", targetLanguage)
                .collect { partialResponse ->
                    stringBuilder.append(partialResponse)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting initial greeting: ${e.message}", e)
            return "Hello! There was an issue starting our chat about $topic. Please try again."
        }

        val fullGreeting = stringBuilder.toString().trim()
        return if (fullGreeting.isNotBlank()) {
            fullGreeting
        } else {
            Log.w(TAG, "Generated initial greeting was blank for topic: $topic")
            "Hello! Let's discuss $topic in $targetLanguage." // Fallback
        }
    }

    // Added: New method from interface
    override fun resetSession() {
        if (llmInference == null) {
            Log.w(TAG, "Reset session called when LlmInference engine is not initialized. Aborting.")
            // Optionally, trigger initialize() or set error state
            // _serviceState.value = LlmServiceState.Error("Cannot reset session, engine not initialized.", modelConfig)
            return
        }
        Log.i(TAG, "Resetting LlmInferenceSession for model ${modelConfig.modelName}.")
        try {
            llmSession?.close() // Close existing session
            llmSession = llmInference?.createSession() // Create a new one
            llmSession?.configure( // Re-apply configurations
                temperature = modelConfig.temperature,
                topK = modelConfig.topK,
                topP = modelConfig.topP,
                maxTokens = modelConfig.maxTokens
            )
            _serviceState.value = LlmServiceState.Ready // Assuming session recreation implies Ready
            Log.i(TAG, "LlmInferenceSession reset and configured successfully.")
        } catch (e: Exception) {
            val errorMsg = "Failed to reset LlmInferenceSession: ${e.message}"
            Log.e(TAG, errorMsg, e)
            _serviceState.value = LlmServiceState.Error(errorMsg, modelConfig)
            close() // Critical failure, close everything
        }
    }

    // Updated: Add override, close session first
    override fun close() {
        Log.i(TAG, "close() called for ${modelConfig.modelName}. Current state: ${_serviceState.value}")
        try {
            llmSession?.close()
            Log.d(TAG, "LlmInferenceSession closed.")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during LlmInferenceSession close: ${e.message}", e)
        }
        llmSession = null

        try {
            llmInference?.close()
            Log.d(TAG, "LlmInference engine closed.")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during LlmInference engine close: ${e.message}", e)
        }
        llmInference = null

        if(_serviceState.value !is LlmServiceState.Idle) { // Only log if it wasn't already idle.
            _serviceState.value = LlmServiceState.Idle
            Log.i(TAG, "MediaPipeLlmService closed and state set to Idle.")
        } else {
            Log.d(TAG, "MediaPipeLlmService close() called but was already Idle.")
        }
    }
}
