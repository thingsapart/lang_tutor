package com.thingsapart.langtutor.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession // Restored
import com.google.mediapipe.tasks.genai.llminference.ProgressListener // Restored
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
// Removed: import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
// Removed: import kotlinx.coroutines.channels.SendChannel

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
    private var llmSession: LlmInferenceSession? = null // Restored

    companion object {
        private const val TAG = "MediaPipeLlmService"
    }

    // Updated: Add override
    override suspend fun initialize() {
        Log.i(TAG, "initialize called. Current state: ${_serviceState.value}")
        // Close existing session and engine before re-initializing
        llmSession?.close()
        llmSession = null
        llmInference?.close()
        llmInference = null

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
            val inferenceOptionsBuilder = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(modelConfig.maxTokens) // Output tokens

            ModelManager.mapToMediaPipeBackend(modelConfig.preferredBackend)?.let {
                inferenceOptionsBuilder.setPreferredBackend(it)
                Log.i(TAG, "Set preferred backend to: $it")
            }
            // Example: inferenceOptionsBuilder.setPreferredBackend(LlmInference.Backend.GPU)

            val inferenceOptions = inferenceOptionsBuilder.build()
            llmInference = LlmInference.createFromOptions(context, inferenceOptions)
            Log.i(TAG, "LlmInference engine created for ${modelConfig.modelName}.")

            // Now create the session
            Log.i(TAG, "Creating LlmInferenceSession...")
            val sessionOptionsBuilder = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(modelConfig.temperature)
                .setTopK(modelConfig.topK)
                .setTopP(modelConfig.topP)
                // maxTokens is typically an engine-level option for total output length,
                // but if session options also expose it for context window or other, review API.
                // For now, following user's previous structure where maxTokens was on engine.

            val sessionOptions = sessionOptionsBuilder.build()
            llmSession = LlmInferenceSession.createFromOptions(llmInference, sessionOptions) // Create session from engine

            Log.i(TAG, "LlmInferenceSession created for ${modelConfig.modelName}.")
            _serviceState.value = LlmServiceState.Ready
            Log.i(TAG, "MediaPipe LlmService initialized successfully and is Ready.")

        } catch (e: Exception) {
            val errorMsg = "Failed to initialize MediaPipe LLM engine or session for ${modelConfig.modelName}: ${e.message}"
            Log.e(TAG, errorMsg, e)
            _serviceState.value = LlmServiceState.Error(errorMsg, modelConfig)
            close()
        }
    }

    override fun generateResponse(prompt: String, conversationId: String, targetLanguage: String): Flow<String> {
        if (_serviceState.value !is LlmServiceState.Ready || llmSession == null) {
            val errorMsg = "LlmService is not ready or session is null. Current state: ${_serviceState.value}"
            Log.w(TAG, "generateResponse called when not ready: $errorMsg")
            return callbackFlow { throw IllegalStateException(errorMsg) }
        }

        val fullPrompt = "User: $prompt\nAI:" // Simple prompt, refine as needed
        Log.d(TAG, "Generating response for prompt: \"$fullPrompt\" with model ${modelConfig.modelName}")

        return callbackFlow {
            val progressListener = ProgressListener<String> { partialResult, done ->
                Log.v(TAG, "ProgressListener: Partial='$partialResult', Done=$done")
                try {
                    channel.trySend(partialResult).isSuccess
                    if (done) {
                        channel.close()
                        Log.d(TAG, "ProgressListener: Channel closed.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "ProgressListener: Error sending to channel", e)
                    channel.close(e)
                }
            }

            try {
                 llmSession?.addQueryChunk(fullPrompt) // Add query chunk first
                 llmSession?.generateResponseAsync(progressListener) // Then call generateResponseAsync without prompt
            } catch (e: Exception) {
                Log.e(TAG, "Exception calling generateResponseAsync: ${e.message}", e)
                channel.close(e)
            }

            awaitClose { Log.d(TAG, "callbackFlow awaitClose for prompt: \"$fullPrompt\"") }
        }
    }

    override suspend fun getInitialGreeting(topic: String, targetLanguage: String): String {
         if (_serviceState.value !is LlmServiceState.Ready || llmSession == null) { // Check llmSession
            val errorMsg = "LlmService is not ready or session is null when getting initial greeting. State: ${_serviceState.value}"
            Log.w(TAG, errorMsg)
            return "Hello! I'm currently unable to generate a full greeting. Let's talk about $topic."
        }
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

    override fun resetSession() { // Changed signature to override fun
        Log.i(TAG, "resetSession called.")
        if (llmInference == null) {
            Log.e(TAG, "Cannot reset session because LlmInference engine is null. Service needs initialization.")
            _serviceState.value = LlmServiceState.Error("Reset failed: LLM engine not initialized.", modelConfig)
            return
        }
        _serviceState.value = LlmServiceState.Initializing // Or a new "Resetting" state if desired
        try {
            llmSession?.close()
            val sessionOptions = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(modelConfig.temperature)
                .setTopK(modelConfig.topK)
                .setTopP(modelConfig.topP)
                .build()

            // llmInference is confirmed not null here by the check above
            llmSession = LlmInferenceSession.createFromOptions(llmInference!!, sessionOptions)
            _serviceState.value = LlmServiceState.Ready

            Log.i(TAG, "LlmInferenceSession reset and configured successfully.")
        } catch (e: Exception) {
            val errorMsg = "Failed to reset LlmInferenceSession: ${e.message}"
            Log.e(TAG, errorMsg, e)
            _serviceState.value = LlmServiceState.Error(errorMsg, modelConfig)
        }
    }

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

        if (_serviceState.value !is LlmServiceState.Idle) {
            _serviceState.value = LlmServiceState.Idle
            Log.i(TAG, "MediaPipeLlmService closed and state set to Idle.")
        } else {
            Log.d(TAG, "MediaPipeLlmService close() called but was already Idle.")
        }
    }
}