package com.thingsapart.langtutor.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
// Removed: import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
// Removed: import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow // Added import for flow builder
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
    private var activeResponseChannel: SendChannel<String>? = null // Added for managing callback

    companion object {
        private const val TAG = "MediaPipeLlmService"
    }

    // Updated: Add override
    override suspend fun initialize() {
        Log.i(TAG, "initialize called. Current state: ${_serviceState.value}")
        // Close existing resources before re-initializing
        llmInference?.close()
        llmInference = null
        activeResponseChannel?.close()
        activeResponseChannel = null

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
                .setMaxTokens(modelConfig.maxTokens) // modelConfig.maxTokens refers to output tokens
                // .setTopK(modelConfig.topK) // topK is a session param usually, or part of prompt
                // .setTemperature(modelConfig.temperature) // temperature is a session param usually
                // .setRandomSeed(modelConfig.randomSeed) // If available in your modelConfig and API
                .setResultListener { partialResult, done ->
                    Log.v(TAG, "ResultListener: Partial='$partialResult', Done=$done")
                    activeResponseChannel?.let { channel ->
                        try {
                            channel.trySend(partialResult).isSuccess
                            if (done) {
                                channel.close()
                                Log.d(TAG, "ResultListener: Channel closed.")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "ResultListener: Error sending to channel", e)
                            channel.close(e)
                        }
                    }
                }
                .setErrorListener { runtimeError ->
                    Log.e(TAG, "LlmInference ErrorListener: code=${runtimeError.errorCode()}, message=${runtimeError.errorMessage()}")
                    activeResponseChannel?.close(RuntimeException("LLM Inference Error: ${runtimeError.errorMessage()}"))
                    _serviceState.value = LlmServiceState.Error("LLM runtime error: ${runtimeError.errorMessage()}", modelConfig)
                }

            ModelManager.mapToMediaPipeBackend(modelConfig.preferredBackend)?.let {
                optionsBuilder.setPreferredBackend(it)
                Log.i(TAG, "Set preferred backend to: $it")
            }
             // Example: optionsBuilder.setPreferredBackend(LlmInference.Backend.GPU)

            val options = optionsBuilder.build()
            llmInference = LlmInference.createFromOptions(context, options)
            Log.i(TAG, "LlmInference engine created for ${modelConfig.modelName}.")
            _serviceState.value = LlmServiceState.Ready
            Log.i(TAG, "MediaPipe LlmService initialized successfully and is Ready.")

        } catch (e: Exception) {
            val errorMsg = "Failed to initialize MediaPipe LLM engine for ${modelConfig.modelName}: ${e.message}"
            Log.e(TAG, errorMsg, e)
            _serviceState.value = LlmServiceState.Error(errorMsg, modelConfig)
            close()
        }
    }

    override fun generateResponse(prompt: String, conversationId: String, targetLanguage: String): Flow<String> {
        if (_serviceState.value !is LlmServiceState.Ready || llmInference == null) {
            val errorMsg = "LlmService is not ready. Current state: ${_serviceState.value}"
            Log.w(TAG, "generateResponse called when not ready: $errorMsg")
            return flow { throw IllegalStateException(errorMsg) }
        }

        // TODO: Incorporate conversationId and targetLanguage into the prompt or session management
        val fullPrompt = "User: $prompt\nAI:" // Simple prompt, refine as needed
        Log.d(TAG, "Generating response for prompt: \"$fullPrompt\" with model ${modelConfig.modelName}")

        return callbackFlow {
            activeResponseChannel?.close(IllegalStateException("New response generation requested, closing previous active channel."))
            activeResponseChannel = channel // Set the current channel

            try {
                // Apply session-specific parameters if needed by the model/API,
                // some models might take these as part of the prompt or have other methods.
                // For LlmInference, topK, temperature etc. are often part of the prompt or fixed model params.
                // If LlmInference API supports dynamic parameters per call, adjust here.
                // For now, assuming they are part of the model or handled by llmInference internally if set in options.
                llmInference?.generateResponseAsync(fullPrompt)
            } catch (e: Exception) {
                Log.e(TAG, "Exception calling generateResponseAsync: ${e.message}", e)
                channel.close(e) // Close new channel if immediate error
                activeResponseChannel = null
            }

            awaitClose {
                Log.d(TAG, "callbackFlow awaitClose for prompt: \"$fullPrompt\"")
                if (activeResponseChannel == channel) { // Clear only if it's still this channel
                    activeResponseChannel = null
                }
            }
        }
    }

    override suspend fun getInitialGreeting(topic: String, targetLanguage: String): String {
         if (_serviceState.value !is LlmServiceState.Ready || llmInference == null) { // Check llmInference
            val errorMsg = "LlmService is not ready or inference engine is null when getting initial greeting. State: ${_serviceState.value}"
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

    // Make it suspend as per plan
    override suspend fun resetSession() {
        Log.i(TAG, "Resetting LlmService by closing existing inference engine and setting state to Idle.")
        close() // This closes llmInference and activeResponseChannel
        // The service will require a call to initialize() again from the outside.
        // No need to explicitly set state to Idle here, close() already does it.
    }

    override fun close() {
        Log.i(TAG, "close() called for ${modelConfig.modelName}. Current state: ${_serviceState.value}")

        try {
            activeResponseChannel?.close()
            Log.d(TAG, "Active response channel closed.")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during activeResponseChannel close: ${e.message}", e)
        }
        activeResponseChannel = null

        try {
            llmInference?.close()
            Log.d(TAG, "LlmInference engine closed.")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during LlmInference engine close: ${e.message}", e)
        }
        llmInference = null

        // Set state to Idle only if it's not already Idle, to avoid redundant logging/notifications
        if (_serviceState.value !is LlmServiceState.Idle) {
            _serviceState.value = LlmServiceState.Idle
            Log.i(TAG, "MediaPipeLlmService closed and state set to Idle.")
        } else {
            Log.d(TAG, "MediaPipeLlmService close() called but was already Idle.")
        }
    }
}
