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

data class AsrModelConfig(
    val modelName: String,
    val internalModelId: String, // Used as filename for the model
    val url: String,
    val vocabUrl: String? = null,
    val vocabFileName: String? = null,
    val isMultilingual: Boolean
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

    // Whisper Models for TFlite.
    //
    // https://huggingface.co/DocWolle/whisper_tflite_models/tree/main
    val WHISPER_BASE_EN_ASR = AsrModelConfig(
        modelName = "Whisper Base ASR",
        internalModelId = "whisper-base.tflite",
        url = "https://huggingface.co/cik009/whisper/resolve/main/whisper-base.en.tflite",
        vocabUrl = "https://huggingface.co/cik009/whisper/resolve/main/filters_vocab_en.bin",
        vocabFileName = "filters_vocab_en.bin",
        isMultilingual = true
    )

    val WHISPER_BASE_ASR = AsrModelConfig(
        modelName = "Whisper Base ASR",
        internalModelId = "whisper-base-transcribe.tflite",
        url = "https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main/whisper-base-transcribe-translate.tflite",
        vocabUrl = "https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main/filters_vocab_multilingual.bin",
        vocabFileName = "filters_vocab_multilingual_translate.bin",
        isMultilingual = true
    )

    // https://huggingface.co/cik009/whisper/tree/main
    val WHISPER_BASE_CIK009_ASR = AsrModelConfig(
        modelName = "Whisper Base ASR",
        internalModelId = "whisper-base-cik009.tflite",
        url = "https://huggingface.co/cik009/whisper/resolve/main/whisper-base.en.tflite",
        vocabUrl = "https://huggingface.co/cik009/whisper/resolve/main/filters_vocab_en.bin",
        vocabFileName = "filters_vocab_en_2_cik009.bin",
        isMultilingual = true
    )

    val WHISPER_DEFAULT_MODEL = WHISPER_BASE_ASR

    val PHI_4_MINI_IT_CPU = LlmModelConfig(
        modelName = "Phi-4 Mini Instruct (CPU)",
        internalModelId = "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task",
        licenseUrl = "https://huggingface.co/microsoft/Phi-4-mini-instruct",
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

    val PHI_4_MINI_IT_GPU = LlmModelConfig(
        modelName = "Phi-4 Mini Instruct (CPU)",
        internalModelId = "Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task",
        licenseUrl = "https://huggingface.co/microsoft/Phi-4-mini-instruct",
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

    val PHI_3_5_MINI_IT_CPU = LlmModelConfig(
        modelName = "Phi-4 Mini Instruct (CPU)",
        internalModelId = "Phi-3_5-mini-instruct_q8_seq1024_ekv1280.task",
        url = "https://huggingface.co/lokinfey/Phi-3.5-instruct-tflite/resolve/main/cpu_mobile_device/phi3.task",
        licenseUrl = "https://huggingface.co/microsoft/Phi-4-mini-instruct",
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

    val PHI_3_5_MINI_IT_GPU = LlmModelConfig(
        modelName = "Phi-4 Mini Instruct (CPU)",
        internalModelId = "Phi-3_5-mini-instruct_q8_seq1024_ekv1280.task",
        url = "https://huggingface.co/lokinfey/Phi-3.5-instruct-tflite/resolve/main/cpu_mobile_device/phi3.task",
        licenseUrl = "https://huggingface.co/microsoft/Phi-4-mini-instruct",
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

    val QWEN_2_5_500M_IT_CPU = LlmModelConfig(
        modelName = "Qwen2.5 0.5B Instruct (CPU)",
        internalModelId = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
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
        internalModelId = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
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

    val QWEN_2_5_1_5_B_IT_CPU = LlmModelConfig(
        modelName = "Qwen2.5 1.5B Instruct (CPU)",
        internalModelId = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv1280.task?download=true",
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

    val QWEN_2_5_1_5_B_IT_GPU = LlmModelConfig(
        modelName = "Qwen2.5 1.5B Instruct (GPU)",
        internalModelId = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        url = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv1280.task?download=true",
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
        internalModelId = "SmolLM-135M-Instruct_seq128_q8_ekv1280.task",
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
        internalModelId = "SmolLM-135M-Instruct_seq128_q8_ekv1280.task",
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
        internalModelId = "gemma-2b-it-gpu-mediapipe-placeholder.task", // Placeholder
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

    val DEFAULT_MODEL = PHI_4_MINI_IT_GPU // Default model

    fun getAllModels(): List<LlmModelConfig> {
        return listOf(
            PHI_3_5_MINI_IT_CPU,
            PHI_3_5_MINI_IT_GPU,
            PHI_4_MINI_IT_CPU,
            PHI_4_MINI_IT_GPU,
            QWEN_2_5_500M_IT_CPU,
            QWEN_2_5_500M_IT_GPU,
            QWEN_2_5_1_5_B_IT_CPU,
            QWEN_2_5_1_5_B_IT_GPU,
            SMOL_135M_CPU,
            SMOL_135M_GPU,
            GEMMA_2B_IT_GPU_MEDIAPIPE_PLACEHOLDER
        ) // Add other models here
    }

    fun getLocalModelFile(context: Context, modelConfig: LlmModelConfig): File {
        // Use internalModelId as the filename to ensure uniqueness
        val file = File(context.filesDir, modelConfig.internalModelId)
        Log.i(TAG, "Model ${modelConfig.modelName} local location ${file.absolutePath}.")
        return file
    }

    fun getLocalModelPath(context: Context, modelConfig: LlmModelConfig): String {
        return getLocalModelFile(context, modelConfig).absolutePath
    }

    fun checkModelExists(context: Context, modelConfig: LlmModelConfig): Boolean {
        return getLocalModelFile(context, modelConfig).exists()
    }

    fun getLocalAsrModelFile(context: Context, modelConfig: AsrModelConfig): File {
        // Use internalModelId as the filename to ensure uniqueness
        // For now, store in the same directory as LLM models.
        // Consider a subdirectory like "asr_models" if needed later.
        val file = File(context.filesDir, modelConfig.internalModelId)
        Log.i(TAG, "ASR Model ${modelConfig.modelName} local location ${file.absolutePath}.")
        return file
    }

    fun getLocalAsrModelPath(context: Context, modelConfig: AsrModelConfig): String {
        return getLocalAsrModelFile(context, modelConfig).absolutePath
    }

    fun checkAsrModelExists(context: Context, modelConfig: AsrModelConfig): Boolean {
        return getLocalAsrModelFile(context, modelConfig).exists()
    }

    fun getLocalAsrVocabFile(context: Context, modelConfig: AsrModelConfig): File? {
        return modelConfig.vocabFileName?.let { File(context.filesDir, it) }
    }

    fun getLocalAsrVocabPath(context: Context, modelConfig: AsrModelConfig): String? {
        return getLocalAsrVocabFile(context, modelConfig)?.absolutePath
    }

    fun checkAsrVocabExists(context: Context, modelConfig: AsrModelConfig): Boolean {
        return getLocalAsrVocabFile(context, modelConfig)?.exists() ?: false
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
