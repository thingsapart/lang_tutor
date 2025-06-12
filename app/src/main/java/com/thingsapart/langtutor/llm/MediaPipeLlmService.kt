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
        closeSilently() // Use a helper to silence exceptions during cleanup

        _serviceState.value = LlmServiceState.Initializing
        Log.i(TAG, "Initializing for model: ${modelConfig.modelName}")

        val modelFileResult = downloadModelIfNeeded()
        if (modelFileResult.isFailure) {
            // Error state already set by downloadModelIfNeeded
            return
        }
        val modelFile = modelFileResult.getOrThrow()

        if (!createEngine(modelFile)) {
            // Error state already set by createEngine
            closeSilently() // Ensure engine is closed if session creation fails or if engine creation failed partially
            return
        }

        if (!createSession()) {
            // Error state already set by createSession
            closeSilently() // Ensure session and engine are closed
            return
        }

        _serviceState.value = LlmServiceState.Ready
        Log.i(TAG, "MediaPipe LlmService initialized successfully and is Ready.")
    }

    private suspend fun downloadModelIfNeeded(): Result<File> {
        if (ModelManager.checkModelExists(context, modelConfig)) {
            val modelFile = ModelManager.getLocalModelFile(context, modelConfig)
            Log.i(TAG, "Model ${modelConfig.modelName} found locally at ${modelFile.absolutePath}")
            return Result.success(modelFile)
        }

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
            return Result.failure(downloadResult.exceptionOrNull() ?: Exception(errorMsg))
        }

        val modelFile = downloadResult.getOrThrow()
        Log.i(TAG, "Model ${modelConfig.modelName} downloaded successfully to ${modelFile.absolutePath}")
        return Result.success(modelFile)
    }

    private fun createEngine(modelFile: File): Boolean {
        Log.i(TAG, "Creating LlmInference engine for ${modelConfig.modelName} at ${modelFile.absolutePath}")
        try {
            val inferenceOptionsBuilder = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(modelConfig.maxTokens)

            ModelManager.mapToMediaPipeBackend(modelConfig.preferredBackend)?.let {
                inferenceOptionsBuilder.setPreferredBackend(it)
                Log.i(TAG, "Set preferred backend to: $it")
            }

            val inferenceOptions = inferenceOptionsBuilder.build()
            llmInference = LlmInference.createFromOptions(context, inferenceOptions)
            Log.i(TAG, "LlmInference engine created for ${modelConfig.modelName}.")
            return true
        } catch (e: Exception) {
            val errorMsg = "Failed to create LlmInference engine for ${modelConfig.modelName}: ${e.message}"
            Log.e(TAG, errorMsg, e)
            _serviceState.value = LlmServiceState.Error(errorMsg, modelConfig)
            llmInference?.close() // Clean up if partially created
            llmInference = null
            return false
        }
    }

    private fun createSession(): Boolean {
        if (llmInference == null) {
            val errorMsg = "Cannot create session because LlmInference engine is null."
            Log.e(TAG, errorMsg)
            _serviceState.value = LlmServiceState.Error(errorMsg, modelConfig)
            return false
        }
        Log.i(TAG, "Creating LlmInferenceSession...")
        try {
            val sessionOptionsBuilder = LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(modelConfig.temperature)
                .setTopK(modelConfig.topK)
                .setTopP(modelConfig.topP)

            val sessionOptions = sessionOptionsBuilder.build()
            llmSession = LlmInferenceSession.createFromOptions(llmInference, sessionOptions)
            Log.i(TAG, "LlmInferenceSession created for ${modelConfig.modelName}.")
            return true
        } catch (e: Exception) {
            val errorMsg = "Failed to create LlmInferenceSession for ${modelConfig.modelName}: ${e.message}"
            Log.e(TAG, errorMsg, e)
            _serviceState.value = LlmServiceState.Error(errorMsg, modelConfig)
            llmSession?.close() // Clean up if partially created
            llmSession = null
            return false
        }
    }

    private fun closeSilently() {
        try {
            llmSession?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Silent close: Exception during LlmInferenceSession close: ${e.message}")
        }
        llmSession = null
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Silent close: Exception during LlmInference engine close: ${e.message}")
        }
        llmInference = null
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
                // llmSession is already checked for nullity by the condition at the start of the method.
                // If it were null, an IllegalStateException would have been thrown.
                llmSession!!.addQueryChunk(fullPrompt) // Add query chunk first
                llmSession!!.generateResponseAsync(progressListener) // Then call generateResponseAsync without prompt
            } catch (e: Exception) {
                Log.e(TAG, "Exception calling addQueryChunk or generateResponseAsync: ${e.message}", e)
                channel.close(e) // Close channel with exception
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

    override fun resetSession() {
        Log.i(TAG, "resetSession called.")
        _serviceState.value = LlmServiceState.Initializing // Or a "Resetting" state

        if (llmInference == null) {
            Log.e(TAG, "Cannot reset session: LlmInference engine is null. Service may need full initialization.")
            _serviceState.value = LlmServiceState.Error("Reset failed: LLM engine not initialized.", modelConfig)
            return
        }

        try {
            llmSession?.close() // Close existing session
            llmSession = null
            Log.i(TAG, "Existing LlmInferenceSession closed.")
        } catch (e: Exception) {
            Log.w(TAG, "Exception closing session during reset: ${e.message}", e)
            // Continue to try creating a new one
        }

        if (createSession()) { // Recreate the session
            _serviceState.value = LlmServiceState.Ready
            Log.i(TAG, "LlmInferenceSession reset and new session created successfully.")
        } else {
            // createSession already sets the error state and logs
            Log.e(TAG, "Failed to create new session during reset.")
            // llmInference is not closed here, as the engine might still be valid.
            // If createSession failed, _serviceState is already Error.
        }
    }

    override fun close() {
        Log.i(TAG, "close() called for ${modelConfig.modelName}. Current state: ${_serviceState.value}")
        try {
            llmSession?.close()
            Log.d(TAG, "LlmInferenceSession closed.")
        } catch (e: Exception) {
            // Log all exceptions but don't let one prevent others from closing
            Log.e(TAG, "Exception during LlmInferenceSession close in close(): ${e.message}", e)
        }
        llmSession = null

        try {
            llmInference?.close()
            Log.d(TAG, "LlmInference engine closed.")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during LlmInference engine close in close(): ${e.message}", e)
        }
        llmInference = null

        // Set to Idle only if not already in an error state from a failed initialization
        // that might have called close().
        if (_serviceState.value !is LlmServiceState.Error) {
             _serviceState.value = LlmServiceState.Idle
        } else if (_serviceState.value is LlmServiceState.Error){
            Log.w(TAG, "MediaPipeLlmService closed, but was already in an Error state.")
        }
        Log.i(TAG, "MediaPipeLlmService close() completed. Final state: ${_serviceState.value}")
    }
}