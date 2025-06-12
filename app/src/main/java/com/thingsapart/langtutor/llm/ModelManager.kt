package com.thingsapart.langtutor.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

// Based on com.google.mediapipe.tasks.genai.llminference.LlmInference.Backend
// Re-define here or ensure it's accessible. For now, re-defining for clarity.
enum class ModelBackend {
    CPU, GPU
}

data class LlmModelConfig(
    val modelName: String, // User-friendly name
    val internalModelId: String, // Unique ID, also used as filename
    val url: String,
    val licenseUrl: String,
    val needsAuth: Boolean, // Future use for HuggingFace token etc.
    val preferredBackend: ModelBackend?,
    val temperature: Float,
    val topK: Int,
    val topP: Float,
    val maxTokens: Int = 2048, // Default max tokens
    val thinkingIndicator: Boolean = false // If model shows a 'thinking' indicator
)

object ModelManager {
    val QWEN_2_5_500M_IT_CPU = LlmModelConfig(
        modelName = "Qwen2.5 0.5B Instruct (CPU)",
        internalModelId = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.tflite",
        url = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_seq128_q8_ekv1280.tflite?download=true",
        licenseUrl = "https://huggingface.co/Qwen/Qwen2.5-72B-Instruct/blob/main/LICENSE",
        needsAuth = false,
        preferredBackend = ModelBackend.CPU,
        thinkingIndicator = false,
        temperature = 0.7f,
        topK = 20,
        topP = 0.8f,
        maxTokens = 2048
    )

    val QWEN_2_5_500M_IT_GPU = LlmModelConfig(
        modelName = "Qwen2.5 0.5B Instruct (GPU)",
        internalModelId = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.tflite",
        url = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_seq128_q8_ekv1280.tflite?download=true",
        licenseUrl = "https://huggingface.co/Qwen/Qwen2.5-72B-Instruct/blob/main/LICENSE",
        needsAuth = false,
        preferredBackend = ModelBackend.GPU,
        thinkingIndicator = false,
        temperature = 0.7f,
        topK = 20,
        topP = 0.8f,
        maxTokens = 2048
    )

    val SMOL_135M_CPU = LlmModelConfig(
        modelName = "SmolLM 135M IT (CPU)",
        internalModelId = "olLM-135M-Instruct_seq128_q8_ekv1280.tflite",
        url = "https://huggingface.co/litert-community/SmolLM-135M-Instruct/resolve/main/SmolLM-135M-Instruct_seq128_q8_ekv1280.tflite?download=true",
        licenseUrl = "https://huggingface.co/Qwen/Qwen2.5-72B-Instruct/blob/main/LICENSE",
        needsAuth = false,
        preferredBackend = ModelBackend.CPU,
        thinkingIndicator = false,
        temperature = 0.7f,
        topK = 20,
        topP = 0.8f,
        maxTokens = 2048
    )

    val SMOL_135M_GPU = LlmModelConfig(
        modelName = "SmolLM 135M IT (GPU)",
        internalModelId = "olLM-135M-Instruct_seq128_q8_ekv1280.tflite",
        url = "https://huggingface.co/litert-community/SmolLM-135M-Instruct/resolve/main/SmolLM-135M-Instruct_seq128_q8_ekv1280.tflite?download=true",
        licenseUrl = "https://huggingface.co/Qwen/Qwen2.5-72B-Instruct/blob/main/LICENSE",
        needsAuth = false,
        preferredBackend = ModelBackend.GPU,
        thinkingIndicator = false,
        temperature = 0.7f,
        topK = 20,
        topP = 0.8f,
        maxTokens = 2048
    )

    // Add more models as needed, e.g., from the user's example list if GGUF versions are available.
    // For now, keeping it to these two as examples.

    val DEFAULT_MODEL = SMOL_135M_CPU // Default model to use

    fun getAllModels(): List<LlmModelConfig> {
        return listOf(QWEN_2_5_500M_IT_CPU, SMOL_135M_CPU) // Add other models here
    }

    fun getLocalModelFile(context: Context, modelConfig: LlmModelConfig): File {
        // Use internalModelId as the filename to ensure uniqueness
        return File(context.filesDir, modelConfig.internalModelId)
    }

    fun getLocalModelPath(context: Context, modelConfig: LlmModelConfig): String {
        return getLocalModelFile(context, modelConfig).absolutePath
    }

    fun checkModelExists(context: Context, modelConfig: LlmModelConfig): Boolean {
        return getLocalModelFile(context, modelConfig).exists()
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
