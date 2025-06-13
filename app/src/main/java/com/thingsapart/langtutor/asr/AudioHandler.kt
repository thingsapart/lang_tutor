package com.thingsapart.langtutor.asr

import android.content.Context
import android.util.Log
import com.whispertflite.asr.Recorder
import com.whispertflite.asr.Whisper
import com.whispertflite.asr.WhisperResult
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException

class AudioHandler(
    private val context: Context,
    private val modelPath: String,
    private val vocabPath: String,
    private val isMultilingual: Boolean,
    private val onTranscriptionUpdate: (String) -> Unit,
    private val onRecordingStopped: () -> Unit,
    private val onError: (String) -> Unit,
    private val onSilenceDetected: () -> Unit
) { // Removed Recorder.RecorderListener, Whisper.WhisperListener

    private companion object {
        private const val TAG = "AudioHandler"
        private const val SILENCE_THRESHOLD_MS = 5000L
    }

    private lateinit var whisper: Whisper
    private var recorder: Recorder = Recorder(context)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // private var transcriptionJob: Job? = null // Removed as it's obsolete
    private var silenceJob: Job? = null

    private var currentTranscription: String = ""
    private var lastSpeechTimeMillis: Long = 0

    // New fields for Whisper action and language
    private var currentAction: Whisper.Action = Whisper.Action.TRANSCRIBE
    private var currentLanguageToken: Int = -1 // Default, potentially 'auto' or English if not multilingual

    private var isEngineInitialized = false
    private var isHandlerReady = false

    init {
        Log.d(TAG, "Initializing AudioHandler...")
        recorder.setListener(InternalRecorderListener())
        whisper = Whisper(context)
        whisper.setListener(InternalWhisperListener())
        // val outputDir = context.cacheDir // Removed
        // val outputFile = File(outputDir, "recorder_temp_audio.wav") // Removed
        // recorder.setFilePath(outputFile.absolutePath) // Removed
        // Log.d(TAG, "Recorder WAV output path set to: ${outputFile.absolutePath}") // Removed

        scope.launch {
            try {
                Log.d(TAG, "Initializing Whisper with model: $modelPath, vocab: $vocabPath")
                whisper.loadModel(File(modelPath), File(vocabPath), isMultilingual)
                isEngineInitialized = true // Assume success if no exception
                Log.d(TAG, "Whisper initialized successfully.")

                if (isMultilingual) {
                    // For now, let's default to a generic multilingual token if applicable,
                    // or expect setLanguage to be called.
                    // The example WhisperRecognitionService uses:
                    // langToken = InputLang.getIdForLanguage(InputLang.getLangList(), langCode);
                    // We don't have InputLang here, so we'll use -1 to indicate 'auto' or model default.
                    currentLanguageToken = -1 // Or a specific token if known for 'auto' for multilingual
                    Log.d(TAG, "Multilingual model, lang token set to auto/default: $currentLanguageToken")
                } else {
                    // For English-only models, the language token might not be strictly needed or could be a specific one.
                    // The Whisper class/engine might default correctly if token is -1 or not set for non-multilingual.
                    currentLanguageToken = -1 // Assuming -1 is fine for English-only default
                    Log.d(TAG, "English-only model, lang token set to default: $currentLanguageToken")
                }
                isHandlerReady = true
            } catch (e: IOException) {
                Log.e(TAG, "IOException during Whisper initialization: ${e.message}", e)
                onError("ASR Engine initialization error: ${e.message}")
                isEngineInitialized = false
                isHandlerReady = false
            } catch (e: Exception) {
                Log.e(TAG, "General Exception during Whisper initialization: ${e.message}", e)
                onError("ASR Engine critical error: ${e.message}")
                isEngineInitialized = false
                isHandlerReady = false
            }
        }
    }

    private fun startWhisperProcessing() {
        if (!isEngineInitialized) {
            Log.e(TAG, "Whisper not initialized, cannot start processing.")
            onError("ASR engine not ready.")
            return
        }
        if (whisper.isInProgress) {
            Log.d(TAG, "Whisper processing is already in progress.")
            return
        }
        scope.launch {
            Log.d(TAG, "Starting Whisper processing. Action: $currentAction, Language Token: $currentLanguageToken")
            whisper.setLanguage(currentLanguageToken) // Set language
            whisper.setAction(currentAction)         // Set action (transcribe/translate)
            whisper.start()                          // Start processing
            // Optional: Provide some feedback that processing has started
            // withContext(Dispatchers.Main) {
            //     onTranscriptionUpdate("Processing...") // Or a more specific status update
            // }
        }
    }

    fun startRecording() {
        if (!isHandlerReady) {
            onError("Audio system not ready or engine failed to initialize.")
            return
        }
        if (recorder.isInProgress) {
            Log.d(TAG, "Recording is already in progress.")
            return
        }

        scope.launch {
            try {
                currentTranscription = ""
                onTranscriptionUpdate("") // Clear previous transcription
                recorder.initVad() // Initialize VAD
                recorder.start() // This is asynchronous as per Recorder.java
                Log.d(TAG, "Recorder start requested.")
                // onUpdateReceived will indicate actual start
                // Start silence detection job
                lastSpeechTimeMillis = System.currentTimeMillis()
                silenceJob?.cancel() // Cancel previous job if any
                silenceJob = launch {
                    while (isActive && recorder.isInProgress) {
                        delay(500) // Check every 500ms
                        if (System.currentTimeMillis() - lastSpeechTimeMillis > SILENCE_THRESHOLD_MS) {
                            // Always call onSilenceDetected. ChatScreen will check its own state (e.g., inputText).
                            Log.d(TAG, "Silence detected, notifying listener.")
                            withContext(Dispatchers.Main) { // Ensure callback to ChatScreen is on Main thread
                                onSilenceDetected()
                            }
                            lastSpeechTimeMillis = System.currentTimeMillis() // Reset silence timer
                            // DO NOT call stopRecording() here.
                            // DO NOT break; the loop should continue to monitor for more silence.
                            Log.d(TAG, "Silence timer reset. VAD continues monitoring.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting recording: ${e.message}", e)
                onError("Start recording failed: ${e.message}")
            }
        }
    }

    fun stopRecording() {
        scope.launch {
            try {
                silenceJob?.cancel() // Stop silence detection
                if (recorder.isInProgress) {
                    recorder.stop() // This is synchronous and waits for file to be saved
                    Log.d(TAG, "Recorder stop requested and completed.")
                } else {
                    Log.d(TAG, "Recorder was not in progress.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recording: ${e.message}", e)
                onError("Stop recording failed: ${e.message}")
            } finally {
                // onRecordingStopped() // Recorder.java's onUpdateReceived("Recording done...!") will handle this
                // and ChatScreen will set isRecording = false.
                // Let's explicitly call it from here after recorder.stop() to ensure UI consistency.
            // Ensure onRecordingStopped is called even if the parent scope is cancelling.
            // The callback itself (onRecordingStopped) is responsible for being safe if its receiver (ChatScreen) is disposed.
            // It runs on Dispatchers.Main.
            val currentOnRecordingStoppedCallback = onRecordingStopped // Capture for safety in launch
            scope.launch(Dispatchers.Main + NonCancellable) {
                currentOnRecordingStoppedCallback()
            }
            }
        }
    }

    // --- Listener Implementations ---

    private inner class InternalRecorderListener : Recorder.RecorderListener {
        override fun onUpdateReceived(message: String?) {
            message?.let {
                Log.d(TAG, "Recorder Update: $it") // TAG should be accessible from AudioHandler
                when (it) {
                    Recorder.MSG_RECORDING_DONE -> {
                        Log.d(TAG, "Recording done, starting Whisper processing.")
                        startWhisperProcessing() // Method in AudioHandler
                    }
                    Recorder.MSG_RECORDING_ERROR -> {
                        Log.e(TAG, "Recorder reported an error.")
                        onError("Recording failed.") // Method/callback in AudioHandler
                        // Ensure UI is updated that recording has stopped.
                        scope.launch(Dispatchers.Main) { // scope and Dispatchers from AudioHandler/imports
                             onRecordingStopped() // Method/callback in AudioHandler
                         }
                    }
                    Recorder.MSG_RECORDING -> {
                        Log.d(TAG, "Recorder has started recording (VAD detected speech or VAD disabled).")
                        // Potentially update UI: e.g., onIsActuallyRecording(true)
                    }
                }
            }
        }
    }

    private inner class InternalWhisperListener : Whisper.WhisperListener {
        override fun onUpdateReceived(message: String) { // This is WhisperListener's onUpdateReceived
            Log.d(TAG, "Whisper Update: $message") // TAG from AudioHandler
            // Example: You could use specific messages to update UI
            // if (message == Whisper.MSG_PROCESSING) {
            //    // Update UI to show "Processing..."
            // } else if (message == Whisper.MSG_PROCESSING_DONE) {
            //    // Update UI to show "Done."
            // }
        }

        override fun onResultReceived(whisperResult: WhisperResult) {
            val transcribedText = whisperResult.result?.trim() ?: ""
            Log.d(TAG, "Whisper Result: '$transcribedText'") // TAG from AudioHandler

            if (transcribedText.isNotBlank()) {
                currentTranscription = transcribedText // currentTranscription in AudioHandler
                scope.launch(Dispatchers.Main) { // scope and Dispatchers from AudioHandler/imports
                    onTranscriptionUpdate(currentTranscription) // Method/callback in AudioHandler
                    lastSpeechTimeMillis = System.currentTimeMillis() // lastSpeechTimeMillis in AudioHandler
                }
            } else {
                Log.d(TAG, "Whisper returned an empty or blank result.")
            }
        }
    }

    fun setAction(action: Whisper.Action) {
        Log.d(TAG, "Setting action to: ${action.name}")
        this.currentAction = action
    }

    fun setLanguage(languageCode: String) {
        Log.d(TAG, "Attempting to set language to code: $languageCode")
        // In a more complete implementation, this is where languageCode would be converted to a languageToken.
        // For example, using a utility like InputLang.getIdForLanguage(InputLang.getLangList(), languageCode).
        // Since InputLang is not confirmed to be available here, we'll keep it simple.
        // We can default to -1 (auto/model default) or handle a few common codes explicitly if desired.
        // For now, let's assume multilingual models can handle a generic token or that the model default works.
        if (isMultilingual) {
            // A real mapping would go here. For now, just logging and keeping token as is or setting to auto.
            // If languageCode is "auto", token should be -1.
            // If specific codes like "en", "es" are passed, they would be mapped to specific tokens.
            // For this step, we'll just log and set a generic token, assuming Whisper handles -1 as auto.
            this.currentLanguageToken = -1 // Placeholder for 'auto' or default for multilingual
            Log.d(TAG, "Language set to '$languageCode'. Multilingual model, using token: ${this.currentLanguageToken} (auto/default). Actual token mapping needed for specific language selection.")
        } else {
            this.currentLanguageToken = -1 // For non-multilingual, token is likely ignored or fixed.
            Log.d(TAG, "Language set to '$languageCode'. Non-multilingual model, token: ${this.currentLanguageToken}.")
        }
        // To make this more functional for demonstration if InputLang is truly unavailable,
        // one could add a simple map here for a few languages:
        // val langMap = mapOf("en" to 0, "es" to 1, "auto" to -1) // Example tokens
        // this.currentLanguageToken = langMap[languageCode.lowercase()] ?: -1
    }

    fun release() {
        Log.d(TAG, "Releasing AudioHandler resources...")
        scope.launch {
            silenceJob?.cancel()
            // transcriptionJob?.cancel() // Removed
            if (recorder.isInProgress) {
                recorder.stop()
            }
            // No explicit recorder.release() method in Recorder.java.
            // Whisper deinitialization
            if (isEngineInitialized) {
                try {
                    whisper.stop() // Stop any ongoing Whisper processing
                    whisper.unloadModel() // Unload the Whisper model
                    isEngineInitialized = false
                    Log.d(TAG, "Whisper model unloaded and processing stopped.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error during Whisper deinitialization: ${e.message}", e)
                }
            }
            isHandlerReady = false
        }.invokeOnCompletion {
            scope.cancel() // Cancel the scope itself after all cleanup jobs complete
            Log.d(TAG, "AudioHandler scope cancelled.")
        }
    }
}
