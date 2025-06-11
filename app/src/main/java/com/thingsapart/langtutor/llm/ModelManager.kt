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

    // Example Models (focused on LLM for now as requested)
    // Using a simple ID that can also serve as a filename.
    // Paths are relative to context.filesDir
    val GEMMA_2B_IT_CPU = LlmModelConfig(
        modelName = "Gemma 2B IT (CPU)",
        internalModelId = "gemma-2b-it-cpu.gguf", // Example, actual filename from URL or specific conversion
        url = "https://huggingface.co/google/gemma-2b-it-gguf/resolve/main/gemma-2b-it-q8_0.gguf", // Example GGUF URL
        licenseUrl = "https://huggingface.co/google/gemma-2b-it",
        needsAuth = false,
        preferredBackend = ModelBackend.CPU,
        thinkingIndicator = false,
        temperature = 0.9f,
        topK = 1,
        topP = 1.0f,
        maxTokens = 2048
    )

    val PHI_3_MINI_CPU = LlmModelConfig(
        modelName = "Phi-3 Mini Instruct (CPU)",
        internalModelId = "phi-3-mini-instruct-cpu.gguf", // Example
        url = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf", // Example GGUF URL
        licenseUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct",
        needsAuth = false,
        preferredBackend = ModelBackend.CPU,
        thinkingIndicator = false,
        temperature = 0.7f,
        topK = 50,
        topP = 0.95f,
        maxTokens = 4096
    )

    // Add more models as needed, e.g., from the user's example list if GGUF versions are available.
    // For now, keeping it to these two as examples.

    val DEFAULT_MODEL = GEMMA_2B_IT_CPU // Default model to use

    fun getAllModels(): List<LlmModelConfig> {
        return listOf(GEMMA_2B_IT_CPU, PHI_3_MINI_CPU) // Add other models here
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
