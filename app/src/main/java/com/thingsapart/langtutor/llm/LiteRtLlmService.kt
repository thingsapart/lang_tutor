package com.thingsapart.langtutor.llm

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.metadata.MetadataExtractor
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.FloatBuffer // Or IntBuffer, depending on model output
import java.nio.IntBuffer

class LiteRtLlmService(
    private val context: Context,
    private val modelConfig: LlmModelConfig = ModelManager.DEFAULT_MODEL, // Ensure ModelManager and LlmModelConfig are accessible
    private val modelDownloader: ModelDownloader // Ensure ModelDownloader is accessible
) : LlmService {

    private val _serviceState = MutableStateFlow<LlmServiceState>(LlmServiceState.Idle)
    override val serviceState: StateFlow<LlmServiceState> = _serviceState.asStateFlow()

    private var interpreter: Interpreter? = null
    private val vocabularyMap = mutableMapOf<String, Int>()
    // unknownTokenId will now be derived from modelConfig.padTokenId in tokenizeText

    companion object {
        private const val TAG = "LiteRtLlmService"
        // Constants for tokenization, e.g., START_TOKEN, END_TOKEN, PAD_TOKEN if model requires
    }

    override suspend fun initialize() {
        Log.i(TAG, "initialize called. Current state: ${_serviceState.value}")
        close() // Close existing interpreter before re-initializing

        _serviceState.value = LlmServiceState.Initializing
        Log.i(TAG, "Initializing LiteRT for model: ${modelConfig.modelName}")

        val modelFile: File
        if (!ModelManager.checkModelExists(context, modelConfig)) {
            Log.i(TAG, "Model ${modelConfig.modelName} not found. Downloading from ${modelConfig.url}")
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
            Log.i(TAG, "Model ${modelConfig.modelName} downloaded to ${modelFile.absolutePath}")
        } else {
            modelFile = ModelManager.getLocalModelFile(context, modelConfig)
            Log.i(TAG, "Model ${modelConfig.modelName} found at ${modelFile.absolutePath}")
        }

        try {
            withContext(Dispatchers.IO) {
                val liteRtBuffer = FileUtil.loadMappedFile(context, modelFile.absolutePath)
                Log.i(TAG, "Loaded LiteRT buffer from ${modelFile.absolutePath}")

                // Load vocabulary from metadata
                loadModelMetadata(liteRtBuffer.duplicate()) // Use duplicate for metadata to avoid position issues

                val interpreterOptions = Interpreter.Options()
                // Configure based on preferredBackend
                when (modelConfig.preferredBackend) {
                    ModelBackend.GPU -> {
                        // Add GPU delegate if available and compatible.
                        // This is a simplified example. Real GPU delegate setup might involve
                        // checking for GpuDelegateFactory and GpuDelegateHelper.
                        // For now, we'll just log the intent or add a placeholder.
                        // A more complete implementation would be:
                        // try {
                        //     val delegateOptions = GpuDelegateHelper.getBestOptionsForDevice()
                        //     interpreterOptions.addDelegate(GpuDelegate(delegateOptions))
                        //     Log.i(TAG, "GPU delegate added.")
                        // } catch (e: Exception) {
                        //     Log.e(TAG, "Failed to add GPU delegate: ${e.message}", e)
                        //     // Fallback to CPU or let it proceed without delegate
                        // }
                        Log.i(TAG, "Attempting to configure for GPU backend (actual delegate setup might be more complex).")
                        // Placeholder: Add actual GPU delegate if the library is present and configured
                        // For now, this primarily serves as a hook for future enhancement.
                    }
                    ModelBackend.CPU -> {
                        // Potentially set number of threads, e.g., interpreterOptions.setNumThreads(4)
                        // For now, default CPU options are often fine.
                        Log.i(TAG, "Configuring for CPU backend.")
                    }
                    null -> {
                        Log.i(TAG, "No preferred backend specified, using default Interpreter options.")
                    }
                }
                interpreter = Interpreter(liteRtBuffer, interpreterOptions)
            }
            _serviceState.value = LlmServiceState.Ready
            Log.i(TAG, "LiteRT LlmService initialized successfully and is Ready.")

        } catch (e: Exception) {
            val errorMsg = "Failed to initialize LiteRT engine for ${modelConfig.modelName}: ${e.message}"
            Log.e(TAG, errorMsg, e)
            _serviceState.value = LlmServiceState.Error(errorMsg, modelConfig)
            close() // Clean up
        }
    }

    private fun loadModelMetadata(modelByteBuffer: ByteBuffer) {
        try {
            val metadataExtractor = MetadataExtractor(modelByteBuffer)
            if (metadataExtractor.hasMetadata()) {
                val vocabFileName = modelConfig.vocabFileNameInMetadata
                if (metadataExtractor.getAssociatedFile(vocabFileName) != null) {
                    val vocabularyStream = metadataExtractor.getAssociatedFile(vocabFileName)
                    vocabularyMap.clear() // Clear previous vocab
                    vocabularyMap.putAll(getVocabulary(vocabularyStream))
                    Log.i(TAG, "Successfully loaded vocabulary from metadata ('$vocabFileName'). Size: ${vocabularyMap.size}")
                } else {
                    Log.w(TAG, "Vocabulary file '$vocabFileName' not found in model metadata.")
                    // Handle cases where vocab might not be present or named differently
                }
            } else {
                Log.w(TAG, "No metadata found in the model.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model metadata: ${e.message}", e)
            // Decide if this is a critical error (e.g., throw) or if service can proceed without vocab
        }
    }

    private fun getVocabulary(inputStream: InputStream): Map<String, Int> {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val map = mutableMapOf<String, Int>()
        var index = 0
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            line?.let { map[it] = index++ }
        }
        reader.close()
        Log.d(TAG, "Vocabulary loaded: ${map.size} entries")
        return map
    }

    private fun tokenizeText(inputText: String, maxTokens: Int): IntArray {
        // Simple punctuation removal and splitting by space.
        // This needs to be robust and match the model's expected tokenization.
        val cleanedText = removePunctuation(inputText.lowercase()) // Consider case sensitivity
        val words = cleanedText.split(" ").filter { it.isNotBlank() }

        val tokenIds = mutableListOf<Int>()

        // Add BOS token if configured
        modelConfig.bosTokenId?.let { tokenIds.add(it) }

        for (word in words) {
            tokenIds.add(vocabularyMap[word] ?: modelConfig.padTokenId) // Use padTokenId for unknown words
        }

        // Add EOS token if configured
        modelConfig.eosTokenId?.let {
            if (tokenIds.size < maxTokens) { // Only add EOS if there's space
                tokenIds.add(it)
            }
        }

        // Pad or truncate to maxTokens
        val finalTokenIds = IntArray(maxTokens) { modelConfig.padTokenId }
        for (i in 0 until kotlin.math.min(tokenIds.size, maxTokens)) {
            finalTokenIds[i] = tokenIds[i]
        }

        Log.d(TAG, "Tokenized text '$inputText' to IDs: ${finalTokenIds.joinToString()}")
        return finalTokenIds
    }

    private fun removePunctuation(text: String): String {
        // This is a very basic punctuation remover.
        // Models often have specific preprocessing requirements.
        return text.replace("[^a-zA-Z0-9\s]".toRegex(), "")
    }

    private fun detokenizeResponse(outputIds: IntArray): String {
        // This is a simplified detokenizer. Actual implementation might be more complex.
        val reversedVocab = vocabularyMap.entries.associateBy({ it.value }) { it.key }
        return outputIds.mapNotNull { id ->
            // Stop decoding at EOS token or PAD token if necessary
            if (id == modelConfig.eosTokenId || id == modelConfig.padTokenId) {
                return@mapNotNull null
            }
            // Use padTokenId as fallback for unknown tokens if not found in reversedVocab, though <UNK> is conventional
            reversedVocab[id] ?: "<UNK>"
        }.joinToString(" ").replace(" <SEP>", "\n") // Example post-processing
    }


    override fun generateResponse(prompt: String, conversationId: String, targetLanguage: String): Flow<String> = callbackFlow {
        if (_serviceState.value !is LlmServiceState.Ready || interpreter == null) {
            val errorMsg = "LiteRT LlmService is not ready. Current state: ${_serviceState.value}"
            Log.w(TAG, "generateResponse called when not ready: $errorMsg")
            close(IllegalStateException(errorMsg))
            return@callbackFlow
        }
        Log.d(TAG, "Generating response for prompt: \"$prompt\" with model ${modelConfig.modelName}")

        try {
            val inputTensor = interpreter!!.getInputTensor(0)
            val outputTensor = interpreter!!.getOutputTensor(0)

            val maxInputTokens = inputTensor.shape()[1] // Assuming shape is [batch, sequence_length]
            val tokenizedPrompt = tokenizeText(prompt, maxInputTokens)

            val inputBuffer = IntBuffer.allocate(maxInputTokens)
            inputBuffer.put(tokenizedPrompt)
            inputBuffer.rewind()

            // Assuming model is auto-regressive and generates token by token or a sequence.
            // This part needs to be adapted based on how the specific TFLite LLM works.
            // For this example, let's assume it generates a full sequence in one go.
            // For token-by-token streaming, this loop would be more complex.

            // Output buffer size based on model's output tensor shape
            // Example: If output is [batch, sequence_length, vocab_size] for logits, or [batch, sequence_length] for IDs
            val outputShape = outputTensor.shape()
            val outputBuffer: ByteBuffer // Or FloatBuffer / IntBuffer
            val isRawLogits = outputShape.size == 3 // e.g., [1, seq_len, vocab_size]
            val outputIds = mutableListOf<Int>()

            // Simplified single-pass generation. Real LLMs might need a loop for auto-regressive generation.
            // The example `TextClassificationHelper` used FloatBuffer for classification scores.
            // For generative LLMs, the output might be token IDs directly (IntBuffer) or logits (FloatBuffer).
            // Let's assume IntBuffer for token IDs for now, based on typical generative models.
            // This needs to match the actual model's output tensor type.
            // If model outputs logits, add an argmax step.

            val maxOutputTokens = outputShape[1]
            val outputIntBuffer = IntBuffer.allocate(maxOutputTokens) // Assuming output is Int token IDs

            val startTime = SystemClock.uptimeMillis()
            interpreter!!.run(inputBuffer, outputIntBuffer) // Adjust if output is not IntBuffer
            val inferenceTime = SystemClock.uptimeMillis() - startTime
            Log.i(TAG, "Inference time: $inferenceTime ms")

            outputIntBuffer.rewind()
            val generatedTokenIds = IntArray(outputIntBuffer.remaining())
            outputIntBuffer.get(generatedTokenIds)

            // Detokenize the full response
            val responseText = detokenizeResponse(generatedTokenIds)
            trySend(responseText).isSuccess
            channel.close() // Close after sending the full response

            Log.d(TAG, "Full response generated and sent.")

        } catch (e: Exception) {
            Log.e(TAG, "Exception during LiteRT inference: ${e.message}", e)
            channel.close(e)
        }

        awaitClose { Log.d(TAG, "callbackFlow awaitClose for prompt: \"$prompt\"") }
    }.flowOn(Dispatchers.IO) // Ensure inference runs on a background thread


    override suspend fun getInitialGreeting(topic: String, targetLanguage: String): String {
        if (_serviceState.value !is LlmServiceState.Ready || interpreter == null) {
            val errorMsg = "LlmService is not ready when getting initial greeting. State: ${_serviceState.value}"
            Log.w(TAG, errorMsg)
            return "Hello! I'm currently unable to generate a full greeting for $topic."
        }
        // This prompt might need to be adjusted based on the model's capabilities/training
        val greetingPrompt = "Generate a friendly, engaging opening message for a conversation about '$topic' in $targetLanguage. The message should be welcoming and invite the user to chat."
        Log.i(TAG, "Requesting initial greeting for topic: $topic, language: $targetLanguage")

        val stringBuilder = StringBuilder()
        try {
            // Call the flow version of generateResponse and collect the single emitted item
            generateResponse(greetingPrompt, "initialGreeting", targetLanguage)
                .collect { fullResponse -> // Since it's not streaming token by token in this example
                    stringBuilder.append(fullResponse)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting initial greeting from LiteRT: ${e.message}", e)
            return "Hello! There was an issue starting our chat about $topic. Please try again."
        }

        val fullGreeting = stringBuilder.toString().trim()
        return if (fullGreeting.isNotBlank()) {
            fullGreeting
        } else {
            Log.w(TAG, "Generated initial greeting was blank for topic: $topic")
            // Fallback if the model returns an empty string
            "Hello! Let's discuss $topic in $targetLanguage."
        }
    }

    override fun resetSession() {
        // For LiteRT Interpreter, "resetting session" typically means clearing any state
        // if the model is stateful (e.g., has internal states passed between runs).
        // If the model is stateless (most common for simple TFLite models),
        // there's often nothing explicit to reset beyond ensuring inputs are fresh.
        // If the Interpreter itself holds state that needs resetting, consult LiteRT docs.
        // For this example, we assume a stateless model or one where re-running is enough.
        Log.i(TAG, "resetSession called. For LiteRT, this might be a no-op or re-init if needed.")
        // If specific reset logic for the Interpreter is available, implement it here.
        // For now, we'll assume re-initializing the service is the most robust way if a hard reset is needed.
        // However, the LlmService interface implies a lighter reset.
        // If the model has updatable states (e.g. via updateState() method on Interpreter), reset them.
        // For now, let's log and consider it a soft reset.
        if (interpreter != null) {
            // interpreter.resetVariableTensors() // Example if such API exists and is needed
            _serviceState.value = LlmServiceState.Ready // Assuming it's ready for new input
            Log.i(TAG, "LiteRT session is considered reset. Ready for new input.")
        } else {
            Log.w(TAG, "Interpreter is null, cannot reset session. Service might need initialization.")
            _serviceState.value = LlmServiceState.Error("Reset failed: Interpreter not initialized.", modelConfig)
        }
    }

    override fun close() {
        Log.i(TAG, "close() called for ${modelConfig.modelName}. Current state: ${_serviceState.value}")
        try {
            interpreter?.close()
            interpreter = null
            Log.d(TAG, "LiteRT Interpreter closed.")
        } catch (e: Exception) {
            Log.e(TAG, "Exception during LiteRT Interpreter close: ${e.message}", e)
        }
        vocabularyMap.clear()

        if (_serviceState.value !is LlmServiceState.Idle && _serviceState.value !is LlmServiceState.Error) {
             // Only set to Idle if not already in an error state that should be preserved
            _serviceState.value = LlmServiceState.Idle
            Log.i(TAG, "LiteRtLlmService closed and state set to Idle.")
        } else {
            Log.d(TAG, "LiteRtLlmService close() called but was already Idle or in Error state.")
        }
    }
}
