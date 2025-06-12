package com.thingsapart.langtutor.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.Closeable
//import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

// Based on com.google.mediapipe.tasks.genai.llminference.LlmInference.Backend
// Re-define here or ensure it's accessible. For now, re-defining for clarity.
enum class ModelBackend {
    CPU, GPU
}

enum class LlmBackend {
    MEDIA_PIPE, LITE_RT
}

data class LlmModelConfig(
    val modelName: String, // User-friendly name
    val internalModelId: String, // Unique ID, also used as filename
    val url: String,
    val licenseUrl: String,
    val needsAuth: Boolean, // Future use for HuggingFace token etc.
    val preferredBackend: ModelBackend?,
    val llmBackend: LlmBackend, // New field
    val temperature: Float,
    val topK: Int,
    val topP: Float,
    val maxTokens: Int = 2048, // Default max tokens
    val thinkingIndicator: Boolean = false, // If model shows a 'thinking' indicator
    // New properties for LiteRT
    val padTokenId: Int = 0,
    val bosTokenId: Int? = null,
    val eosTokenId: Int? = null,
    val vocabFileNameInMetadata: String = "vocab.txt"
)

class MappedFile(file: File, mode: FileChannel.MapMode) : Closeable {
    // Private properties for internal resource management
    private val randomAccessFile: RandomAccessFile
    private val fileChannel: FileChannel

    /** The underlying MappedByteBuffer for read/write operations. */
    private val buffer: MappedByteBuffer

    /** The size of the mapped file region in bytes. */
    val size: Long

    init {
        try {
            // Use an if-expression, which is more idiomatic than a ternary operator
            val rafMode = if (mode == FileChannel.MapMode.READ_ONLY) "r" else "rw"

            randomAccessFile = RandomAccessFile(file, rafMode)
            fileChannel = randomAccessFile.channel
            size = fileChannel.size()
            buffer = fileChannel.map(mode, 0, size)
        } catch (e: IOException) {
            // If initialization fails, close any resources that might have been opened
            close()
            throw e // Re-throw the exception to the caller
        }
    }

    /**
     * Forces any changes made to the buffer to be written to the storage device.
     */
    fun save() {
        buffer.force()
    }

    /**
     * Closes the underlying file resources.
     * This is called automatically when using the `use { ... }` block.
     */
    override fun close() {
        fileChannel.close()
        randomAccessFile.close()
    }

    fun getBuffer(): MappedByteBuffer {
        return buffer
    }
}

object ModelManager {
    private const val TAG = "ModelManager"

    val QWEN_2_5_500M_IT_CPU = LlmModelConfig(
        modelName = "Qwen2.5 0.5B Instruct (CPU)",
        internalModelId = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.tflite",
        url = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true",
        licenseUrl = "https://huggingface.co/Qwen/Qwen2.5-72B-Instruct/blob/main/LICENSE",
        needsAuth = false,
        preferredBackend = ModelBackend.CPU,
        llmBackend = LlmBackend.MEDIA_PIPE,
        thinkingIndicator = false,
        temperature = 0.7f,
        topK = 20,
        topP = 0.8f,
        maxTokens = 2048,
        padTokenId = 0,
        bosTokenId = null,
        eosTokenId = null,
        vocabFileNameInMetadata = "vocab.txt"
    )

    val QWEN_2_5_500M_IT_GPU = LlmModelConfig(
        modelName = "Qwen2.5 0.5B Instruct (GPU)",
        internalModelId = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.tflite",
        url = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true",
        licenseUrl = "https://huggingface.co/Qwen/Qwen2.5-72B-Instruct/blob/main/LICENSE",
        needsAuth = false,
        preferredBackend = ModelBackend.GPU,
        llmBackend = LlmBackend.MEDIA_PIPE,
        thinkingIndicator = false,
        temperature = 0.7f,
        topK = 20,
        topP = 0.8f,
        maxTokens = 2048,
        padTokenId = 0,
        bosTokenId = null,
        eosTokenId = null,
        vocabFileNameInMetadata = "vocab.txt"
    )

    val SMOL_135M_CPU = LlmModelConfig(
        modelName = "SmolLM 135M IT (CPU)",
        internalModelId = "SmolLM-135M-Instruct_seq128_q8_ekv1280.tflite",
        url = "https://huggingface.co/litert-community/SmolLM-135M-Instruct/resolve/main/SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true",
        licenseUrl = "https://huggingface.co/Qwen/Qwen2.5-72B-Instruct/blob/main/LICENSE",
        needsAuth = false,
        preferredBackend = ModelBackend.CPU,
        llmBackend = LlmBackend.MEDIA_PIPE,
        thinkingIndicator = false,
        temperature = 0.7f,
        topK = 20,
        topP = 0.8f,
        maxTokens = 2048,
        padTokenId = 0,
        bosTokenId = null,
        eosTokenId = null,
        vocabFileNameInMetadata = "vocab.txt"
    )

    val SMOL_135M_GPU = LlmModelConfig(
        modelName = "SmolLM 135M IT (GPU)",
        internalModelId = "SmolLM-135M-Instruct_seq128_q8_ekv1280.tflite",
        url = "https://huggingface.co/litert-community/SmolLM-135M-Instruct/resolve/main/SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true",
        licenseUrl = "https://huggingface.co/Qwen/Qwen2.5-72B-Instruct/blob/main/LICENSE",
        needsAuth = false,
        preferredBackend = ModelBackend.GPU,
        llmBackend = LlmBackend.MEDIA_PIPE,
        thinkingIndicator = false,
        temperature = 0.7f,
        topK = 20,
        topP = 0.8f,
        maxTokens = 2048,
        padTokenId = 0,
        bosTokenId = null,
        eosTokenId = null,
        vocabFileNameInMetadata = "vocab.txt"
    )

    val GEMMA_2B_IT_GPU_MEDIAPIPE_PLACEHOLDER = LlmModelConfig(
        modelName = "Gemma 2B IT (GPU MediaPipe Placeholder)",
        internalModelId = "gemma-2b-it-gpu-mediapipe-placeholder.tflite", // Placeholder
        url = "https://example.com/gemma-placeholder-model", // Placeholder
        licenseUrl = "https://example.com/gemma-license", // Placeholder
        needsAuth = false,
        preferredBackend = ModelBackend.GPU,
        llmBackend = LlmBackend.MEDIA_PIPE, // Crucial for this test
        temperature = 0.7f,
        topK = 40,
        topP = 0.9f,
        maxTokens = 1024,
        thinkingIndicator = true, // Or false, as appropriate
        // LiteRT specific fields can be default or 0/null as they won't be used by MediaPipeLlmService
        padTokenId = 0,
        bosTokenId = null,
        eosTokenId = null,
        vocabFileNameInMetadata = ""
    )

    // Add more models as needed, e.g., from the user's example list if GGUF versions are available.
    // For now, keeping it to these two as examples.

    val GEMMA3_1B_IT_CPU = LlmModelConfig(
        modelName = "Gemma3 1B IT (CPU)",
        internalModelId = "Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task",
        url = "https://huggingface.co/google/gemma-3-1b-it-tflite/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task?download=true",
        licenseUrl = "https://huggingface.co/google/gemma-3-1b-it-tflite/blob/main/LICENSE.md",
        needsAuth = false,
        preferredBackend = ModelBackend.CPU,
        llmBackend = LlmBackend.MEDIA_PIPE,
        temperature = 0.7f,
        topK = 1,
        topP = 1.0f,
        maxTokens = 2048, // from ekv2048
        thinkingIndicator = false
    )

    val GEMMA_3_1B_IT_GPU = LlmModelConfig(
        modelName = "Gemma3 1B IT (GPU)",
        internalModelId = "Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task",
        url = "https://huggingface.co/google/gemma-3-1b-it-tflite/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task?download=true",
        licenseUrl = "https://huggingface.co/google/gemma-3-1b-it-tflite/blob/main/LICENSE.md",
        needsAuth = false,
        preferredBackend = ModelBackend.GPU,
        llmBackend = LlmBackend.MEDIA_PIPE,
        temperature = 0.7f,
        topK = 1,
        topP = 1.0f,
        maxTokens = 2048, // from ekv2048
        thinkingIndicator = false
    )

    val GEMMA_2_2B_IT_CPU = LlmModelConfig(
        modelName = "Gemma2 2B IT (CPU)",
        internalModelId = "gemma-2-2b-it-cpu-2k.task", // Assuming ekv2048 for 2k
        url = "https://huggingface.co/google/gemma-2-2b-it-tflite/resolve/main/gemma-2-2b-it-cpu-2k.task?download=true",
        licenseUrl = "https://huggingface.co/google/gemma-2-2b-it-tflite/blob/main/LICENSE.md",
        needsAuth = false,
        preferredBackend = ModelBackend.CPU,
        llmBackend = LlmBackend.MEDIA_PIPE,
        temperature = 0.7f,
        topK = 1,
        topP = 1.0f,
        maxTokens = 2048, // from 2k in filename
        thinkingIndicator = false
    )

    val DEEPSEEK_R1_DISTILL_QWEN_1_5_B = LlmModelConfig(
        modelName = "DeepSeek-R1 Distill Qwen 1.5B",
        internalModelId = "DeepSeek-R1_Distill_Qwen1.5-1.5B_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/DeepSeek-R1_Distill_Qwen1.5-1.5B/resolve/main/DeepSeek-R1_Distill_Qwen1.5-1.5B_multi-prefill-seq_q8_ekv1280.task?download=true",
        licenseUrl = "https://huggingface.co/deepseek-ai/DeepSeek-V2-Lite/blob/main/LICENSE",
        needsAuth = false,
        preferredBackend = ModelBackend.CPU, // Defaulting to CPU, can be GPU too
        llmBackend = LlmBackend.MEDIA_PIPE,
        temperature = 0.7f,
        topK = 20,
        topP = 0.8f,
        maxTokens = 1280, // from ekv1280
        thinkingIndicator = true
    )

    val LLAMA_3_2_1B_INSTRUCT = LlmModelConfig(
        modelName = "Llama3.2 1B Instruct",
        internalModelId = "Llama3.2-1B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/Llama3.2-1B-Instruct/resolve/main/Llama3.2-1B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true",
        licenseUrl = "https://huggingface.co/meta-llama/Meta-Llama-3.1-8B-Instruct/blob/main/LICENSE",
        needsAuth = false,
        preferredBackend = ModelBackend.CPU, // Defaulting to CPU
        llmBackend = LlmBackend.MEDIA_PIPE,
        temperature = 0.7f,
        topK = 20,
        topP = 0.8f,
        maxTokens = 1280, // from ekv1280
        thinkingIndicator = true
    )

    val LLAMA_3_2_3B_INSTRUCT = LlmModelConfig(
        modelName = "Llama3.2 3B Instruct",
        internalModelId = "Llama3.2-3B-Instruct_multi-prefill-seq_q8_ekv2048.task",
        url = "https://huggingface.co/litert-community/Llama3.2-3B-Instruct/resolve/main/Llama3.2-3B-Instruct_multi-prefill-seq_q8_ekv2048.task?download=true",
        licenseUrl = "https://huggingface.co/meta-llama/Meta-Llama-3.1-8B-Instruct/blob/main/LICENSE",
        needsAuth = false,
        preferredBackend = ModelBackend.CPU, // Defaulting to CPU
        llmBackend = LlmBackend.MEDIA_PIPE,
        temperature = 0.7f,
        topK = 20,
        topP = 0.8f,
        maxTokens = 2048, // from ekv2048
        thinkingIndicator = true
    )

    val PHI_4_MINI_INSTRUCT = LlmModelConfig(
        modelName = "Phi-4-mini Instruct",
        internalModelId = "Phi-4-mini-Instruct_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/Phi-4-mini-Instruct/resolve/main/Phi-4-mini-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true",
        licenseUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct/blob/main/LICENSE",
        needsAuth = false,
        preferredBackend = ModelBackend.CPU, // Defaulting to CPU
        llmBackend = LlmBackend.MEDIA_PIPE,
        temperature = 0.7f,
        topK = 20,
        topP = 0.8f,
        maxTokens = 1280, // from ekv1280
        thinkingIndicator = true
    )

    val QWEN2_0_5B_INSTRUCT = LlmModelConfig(
        modelName = "Qwen2 0.5B Instruct",
        internalModelId = "Qwen2-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task", // Matches existing Qwen internalId format style
        url = "https://huggingface.co/qwen/Qwen2-0.5B-Instruct-TFLite/resolve/main/Qwen2-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true",
        licenseUrl = "https://huggingface.co/Qwen/Qwen2-beta/blob/main/LICENSE. gikk", // Note: license filename might vary, using placeholder
        needsAuth = false,
        preferredBackend = ModelBackend.CPU, // Defaulting to CPU
        llmBackend = LlmBackend.MEDIA_PIPE,
        temperature = 0.7f,
        topK = 20,
        topP = 0.8f,
        maxTokens = 1280, // from ekv1280
        thinkingIndicator = false // Qwen models were false before
    )

    val QWEN2_1_5B_INSTRUCT = LlmModelConfig(
        modelName = "Qwen2 1.5B Instruct",
        internalModelId = "Qwen2-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/qwen/Qwen2-1.5B-Instruct-TFLite/resolve/main/Qwen2-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true",
        licenseUrl = "https://huggingface.co/Qwen/Qwen2-beta/blob/main/LICENSE.gikk",
        needsAuth = false,
        preferredBackend = ModelBackend.CPU, // Defaulting to CPU
        llmBackend = LlmBackend.MEDIA_PIPE,
        temperature = 0.7f,
        topK = 20,
        topP = 0.8f,
        maxTokens = 1280, // from ekv1280
        thinkingIndicator = false
    )

    val QWEN2_5_3B_INSTRUCT = LlmModelConfig( // Assuming this is a new size, distinct from existing 0.5B
        modelName = "Qwen2.5 3B Instruct",
        internalModelId = "Qwen2.5-3B-Instruct_multi-prefill-seq_q8_ekv2048.task", // Hypothetical filename
        url = "https://huggingface.co/litert-community/Qwen2.5-3B-Instruct/resolve/main/Qwen2.5-3B-Instruct_multi-prefill-seq_q8_ekv2048.task?download=true", // Hypothetical URL
        licenseUrl = "https://huggingface.co/Qwen/Qwen2.5-72B-Instruct/blob/main/LICENSE",
        needsAuth = false,
        preferredBackend = ModelBackend.CPU,
        llmBackend = LlmBackend.MEDIA_PIPE,
        temperature = 0.7f,
        topK = 20,
        topP = 0.8f,
        maxTokens = 2048, // from ekv2048
        thinkingIndicator = false
    )

    val SMOLLM_135M_INSTRUCT = LlmModelConfig( // This seems to be a duplicate of SMOL_135M_CPU/GPU but with .task
        modelName = "SmolLM 135M Instruct (.task)", // Differentiating name slightly
        internalModelId = "SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task", // .task extension
        url = "https://huggingface.co/litert-community/SmolLM-135M-Instruct/resolve/main/SmolLM-135M-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true",
        licenseUrl = "https://huggingface.co/Qwen/Qwen2.5-72B-Instruct/blob/main/LICENSE", // Using Qwen license as per existing SmolLM
        needsAuth = false,
        preferredBackend = ModelBackend.CPU, // Defaulting to CPU
        llmBackend = LlmBackend.MEDIA_PIPE,
        temperature = 0.7f,
        topK = 20,
        topP = 0.8f,
        maxTokens = 1280, // from ekv1280
        thinkingIndicator = false // Consistent with existing SmolLM
    )

    val TINYLLAMA_1_1B_CHAT_V1_0 = LlmModelConfig(
        modelName = "TinyLlama 1.1B Chat v1.0",
        internalModelId = "TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/TinyLlama-1.1B-Chat-v1.0/resolve/main/TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task?download=true",
        licenseUrl = "https://huggingface.co/TinyLlama/TinyLlama-1.1B-Chat-v1.0/blob/main/LICENSE",
        needsAuth = false,
        preferredBackend = ModelBackend.CPU, // Defaulting to CPU
        llmBackend = LlmBackend.MEDIA_PIPE,
        temperature = 0.7f,
        topK = 20,
        topP = 0.8f,
        maxTokens = 1280, // from ekv1280
        thinkingIndicator = true
    )

    val DEFAULT_MODEL = SMOL_135M_CPU // Default model to use

    fun getAllModels(): List<LlmModelConfig> {
        return listOf(
            QWEN_2_5_500M_IT_CPU,
            QWEN_2_5_500M_IT_GPU,
            SMOL_135M_CPU,
            SMOL_135M_GPU,
            GEMMA_2B_IT_GPU_MEDIAPIPE_PLACEHOLDER,
            GEMMA3_1B_IT_CPU,
            GEMMA_3_1B_IT_GPU,
            GEMMA_2_2B_IT_CPU,
            DEEPSEEK_R1_DISTILL_QWEN_1_5_B,
            LLAMA_3_2_1B_INSTRUCT,
            LLAMA_3_2_3B_INSTRUCT,
            PHI_4_MINI_INSTRUCT,
            QWEN2_0_5B_INSTRUCT,
            QWEN2_1_5B_INSTRUCT,
            QWEN2_5_3B_INSTRUCT,
            SMOLLM_135M_INSTRUCT,
            TINYLLAMA_1_1B_CHAT_V1_0
        ) // Add other models here
    }

    fun getLocalModelFile(context: Context, modelConfig: LlmModelConfig): File {
        // Use internalModelId as the filename to ensure uniqueness
        val file = File(context.externalCacheDir, modelConfig.internalModelId)
        Log.i(TAG, "Model ${modelConfig.modelName} local location ${file.absolutePath}.")
        return file
    }

    fun getLocalModelPath(context: Context, modelConfig: LlmModelConfig): String {
        return getLocalModelFile(context, modelConfig).absolutePath
    }

    fun checkModelExists(context: Context, modelConfig: LlmModelConfig): Boolean {
        return getLocalModelFile(context, modelConfig).exists()
    }

    fun getLocalModelMappedFile(context: Context, modelConfig: LlmModelConfig): MappedFile {
        val file = getLocalModelFile(context, modelConfig)
        return MappedFile(file, FileChannel.MapMode.READ_ONLY)
    }

    // Helper to convert our ModelBackend to MediaPipe's Backend
    fun mapToMediaPipeBackend(backend: ModelBackend?): LlmInference.Backend? {
        return when (backend) {
            ModelBackend.CPU -> LlmInference.Backend.CPU
            ModelBackend.GPU -> LlmInference.Backend.GPU
            null -> null
        }
    }
}
