package com.thingsapart.langtutor.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
// import kotlinx.coroutines.flow.flowOf // Not needed if callbackFlow is used for errors too
import org.tensorflow.lite.task.genai.llminference.LlmInference
import org.tensorflow.lite.task.genai.llminference.ProgressListener // Correct import based on typical TFLite task libraries
import java.io.File
import kotlinx.coroutines.launch // For getInitialGreeting if using async and collecting
import kotlinx.coroutines.runBlocking // For getInitialGreeting if using async and collecting, alternative to GlobalScope

class TensorflowLiteLlmService(
    private val context: Context,
    private val modelConfig: LlmModelConfig = ModelManager.DEFAULT_MODEL, // Added default
    private val modelDownloader: ModelDownloader
) : LlmService {

    private val _serviceState = MutableStateFlow<LlmServiceState>(LlmServiceState.Idle)
    override val serviceState: StateFlow<LlmServiceState> = _serviceState.asStateFlow()

    private var llmInference: LlmInference? = null

    companion object {
        private const val TAG = "TensorflowLiteLlmSvc"
    }

    override suspend fun initialize() {
        Log.i(TAG, "initialize called. Current state: ${_serviceState.value}")
        if (_serviceState.value == LlmServiceState.Initializing || _serviceState.value == LlmServiceState.Ready) {
            Log.w(TAG, "Initialization requested while already Initializing or Ready. Current state: ${_serviceState.value}")
            // Potentially return or re-evaluate if re-initialization is truly needed.
            // For now, we allow it, which means close() will be called first.
        }

        // Close existing engine before re-initializing
        close() // Ensures any existing TFLite engine is released and state becomes Idle

        _serviceState.value = LlmServiceState.Initializing
        Log.i(TAG, "Initializing TFLite service for model: ${modelConfig.modelName} (ID: ${modelConfig.internalModelId})")

        val modelFile: File
        try {
            if (!ModelManager.checkModelExists(context, modelConfig)) {
                Log.i(TAG, "Model ${modelConfig.modelName} not found locally. Starting download from ${modelConfig.url}")
                if (modelConfig.url == null) {
                    val noUrlError = "Model ${modelConfig.modelName} (ID: ${modelConfig.internalModelId}) has no download URL specified."
                    Log.e(TAG, noUrlError)
                    _serviceState.value = LlmServiceState.Error(noUrlError, modelConfig)
                    return
                }
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
        } catch (e: Exception) {
            val modelAccessError = "Failed to access or download model ${modelConfig.modelName}: ${e.message}"
            Log.e(TAG, modelAccessError, e)
            _serviceState.value = LlmServiceState.Error(modelAccessError, modelConfig)
            return
        }

        try {
            Log.i(TAG, "Creating TFLite LlmInference engine for ${modelConfig.modelName} at ${modelFile.absolutePath}")
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(modelConfig.maxTokens)
                .setTopK(modelConfig.topK)
                .setTopP(modelConfig.topP)
                .setTemperature(modelConfig.temperature)
                .setRandomSeed(modelConfig.randomSeed)
                .build()

            llmInference = LlmInference.createFromOptions(context, options) // This can throw Exception
            _serviceState.value = LlmServiceState.Ready
            Log.i(TAG, "TensorFlow Lite LlmInference engine created for ${modelConfig.modelName}.")

        } catch (e: Exception) {
            val errorMsg = "Failed to initialize TFLite engine for ${modelConfig.modelName}: ${e.message}"
            Log.e(TAG, errorMsg, e)
            _serviceState.value = LlmServiceState.Error(errorMsg, modelConfig)
            close()
        }
    }

    override fun generateResponse(prompt: String, conversationId: String, targetLanguage: String): Flow<String> {
        if (_serviceState.value !is LlmServiceState.Ready || llmInference == null) {
            val errorMsg = "LlmService is not ready or llmInference is null. Current state: ${_serviceState.value}"
            Log.w(TAG, "generateResponse called when not ready: $errorMsg")
            // Return a flow that emits an error, consistent with callbackFlow usage
            return callbackFlow {
                Log.e(TAG, "generateResponse pre-condition failed: $errorMsg")
                throw IllegalStateException(errorMsg)
            }
        }

        val fullPrompt = "User: $prompt\nAI:" // Simple prompt, refine as needed
        Log.d(TAG, "Generating TFLite response for prompt: \"$fullPrompt\" with model ${modelConfig.modelName}")

        return callbackFlow {
            val progressListener = object : ProgressListener<String> {
                override fun onResult(partialResult: String?, done: Boolean, error: Exception?) {
                    if (error != null) {
                        Log.e(TAG, "TFLite ProgressListener error: ${error.message}", error)
                        channel.close(error) // Close channel with exception
                        return
                    }
                    // partialResult can be null according to the annotation, handle it
                    partialResult?.let {
                        Log.v(TAG, "TFLite ProgressListener: Partial='$it', Done=$done")
                        val sendSuccess = channel.trySend(it).isSuccess
                        if (!sendSuccess) {
                             Log.w(TAG, "TFLite ProgressListener: Failed to send partial result to channel.")
                        }
                    }
                    if (done) {
                        channel.close() // Close channel normally
                        Log.d(TAG, "TFLite ProgressListener: Channel closed (done).")
                    }
                }
            }

            try {
                // Ensure llmInference is not null again due to potential race conditions if not synchronized
                val currentEngine = llmInference
                if (currentEngine == null) {
                    val engineNullError = IllegalStateException("LlmInference became null before calling generateResponseAsync.")
                    Log.e(TAG, "generateResponseAsync call failed: ", engineNullError)
                    channel.close(engineNullError)
                } else {
                    currentEngine.generateResponseAsync(fullPrompt, progressListener)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception calling TFLite generateResponseAsync: ${e.message}", e)
                channel.close(e) // Close channel with exception
            }

            awaitClose { Log.d(TAG, "TFLite callbackFlow awaitClose for prompt: \"$fullPrompt\"") }
        }
    }

    override suspend fun getInitialGreeting(topic: String, targetLanguage: String): String {
        if (_serviceState.value !is LlmServiceState.Ready || llmInference == null) {
            val errorMsg = "LlmService is not ready or llmInference is null for greeting. State: ${_serviceState.value}"
            Log.w(TAG, errorMsg)
            return "Hello! I'm currently unable to generate a full greeting for $topic. Reason: $errorMsg"
        }
        val greetingPrompt = "Generate a friendly, engaging opening message for a conversation about '$topic' in $targetLanguage. The message should be welcoming and encourage the user to start talking."
        Log.i(TAG, "Requesting TFLite initial greeting for topic: $topic, language: $targetLanguage")

        return try {
            // The LlmInference.generateResponse(prompt) method is indeed synchronous (blocking).
            val fullGreeting = llmInference?.generateResponse(greetingPrompt) ?: ""
            if (fullGreeting.isNotBlank()) {
                Log.i(TAG, "Generated TFLite initial greeting: $fullGreeting")
                fullGreeting.trim()
            } else {
                Log.w(TAG, "Generated TFLite initial greeting was blank for topic: $topic")
                "Hello! Let's discuss $topic in $targetLanguage. (Received blank response)" // Fallback
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating TFLite initial greeting: ${e.message}", e)
            "Hello! There was an issue starting our chat about $topic. Error: ${e.message}"
        }
    }

    override fun resetSession() {
        Log.i(TAG, "resetSession called. Current state: ${_serviceState.value}")

        // Regardless of llmInference state, we're guiding towards re-initialization.
        Log.w(TAG, "TFLite LlmInference reset: Performing re-initialization by closing existing engine and setting state to Error to guide client.")

        val modelNameForError = modelConfig.modelName // Capture before close might nullify things if modelConfig was complex

        close() // This sets llmInference to null and state to Idle.

        // Transition to an error state that clearly indicates re-initialization is needed.
        val resetErrorMsg = "Session reset requires re-initialization for model '$modelNameForError'. Please call initialize()."
        Log.i(TAG, resetErrorMsg)
        _serviceState.value = LlmServiceState.Error(resetErrorMsg, modelConfig)
        // No longer setting to Initializing first as close() moves to Idle,
        // and the point is to stop and guide client.
    }

    override fun close() {
        Log.i(TAG, "close() called for ${modelConfig.modelName}. Current llmInference: ${if (llmInference == null) "null" else "not null"}. Current state: ${_serviceState.value}")
        try {
            llmInference?.close()
            Log.d(TAG, "TFLite LlmInference engine closed for ${modelConfig.modelName}.")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during TFLite LlmInference engine close for ${modelConfig.modelName}: ${e.message}", e)
        }
        llmInference = null

        if (_serviceState.value !is LlmServiceState.Idle) {
            // Only move to Idle if not already in Idle.
            // Error states are also reset to Idle by close(), indicating resources are released.
            _serviceState.value = LlmServiceState.Idle
            Log.i(TAG, "TensorflowLiteLlmService for ${modelConfig.modelName} closed and state set to Idle.")
        } else {
            Log.d(TAG, "TensorflowLiteLlmService for ${modelConfig.modelName} close() called but state was already Idle.")
        }
    }
}
