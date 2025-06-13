package com.thingsapart.langtutor.asr

import android.content.Context
import android.util.Log
import com.whispertflite.asr.Recorder
import com.whispertflite.engine.WhisperEngine // Keep interface for type
import com.whispertflite.engine.WhisperEngineJava // For instantiation
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException

class AudioHandler(
    private val context: Context,
    private val modelPath: String,
    private val onTranscriptionUpdate: (String) -> Unit,
    private val onRecordingStopped: () -> Unit,
    private val onError: (String) -> Unit,
    private val onSilenceDetected: () -> Unit
) : Recorder.RecorderListener {

    private companion object {
        private const val TAG = "AudioHandler"
        private const val SILENCE_THRESHOLD_MS = 5000L
    }

    private var whisperEngine: WhisperEngine = WhisperEngineJava(context)
    private var recorder: Recorder = Recorder(context)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var transcriptionJob: Job? = null // For managing specific transcription tasks if needed, or general processing
    private var silenceJob: Job? = null

    private var currentTranscription: String = ""
    private var lastSpeechTimeMillis: Long = 0

    // Assuming vocabPath might be optional or derived. For now, allowing null.
    // If WhisperUtil requires a specific vocab file, this needs to be provided.
    private val vocabPath: String? = null // Or determine if a default/specific path is needed
    private val isMultilingual = false // Defaulting to false for 'whisper-base.tflite'

    private var isEngineInitialized = false
    private var isHandlerReady = false

    init {
        Log.d(TAG, "Initializing AudioHandler...")
        recorder.setListener(this)
        // Define a temporary file path for the recorder's WAV output
        val outputDir = context.cacheDir
        val outputFile = File(outputDir, "recorder_temp_audio.wav")
        recorder.setFilePath(outputFile.absolutePath)
        Log.d(TAG, "Recorder WAV output path set to: ${outputFile.absolutePath}")

        scope.launch {
            try {
                Log.d(TAG, "Initializing WhisperEngine with model: $modelPath, vocab: $vocabPath")
                isEngineInitialized = whisperEngine.initialize(modelPath, vocabPath, isMultilingual)
                if (isEngineInitialized) {
                    Log.d(TAG, "WhisperEngine initialized successfully.")
                    isHandlerReady = true
                } else {
                    Log.e(TAG, "WhisperEngine initialization failed.")
                    onError("ASR Engine failed to initialize.")
                    isHandlerReady = false
                }
            } catch (e: IOException) {
                Log.e(TAG, "IOException during WhisperEngine initialization: ${e.message}", e)
                onError("ASR Engine initialization error: ${e.message}")
                isEngineInitialized = false
                isHandlerReady = false
            } catch (e: Exception) {
                Log.e(TAG, "General Exception during WhisperEngine initialization: ${e.message}", e)
                onError("ASR Engine critical error: ${e.message}")
                isEngineInitialized = false
                isHandlerReady = false
            }
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
                            if (currentTranscription.isNotBlank()) {
                                Log.d(TAG, "Silence detected, triggering send.")
                                onSilenceDetected() // ChatScreen will call stopRecording
                            } else {
                                // If silence detected but nothing transcribed, just stop without sending
                                Log.d(TAG, "Silence detected with no transcription, stopping.")
                                stopRecording() // Stop directly
                            }
                            break // Stop silence detection for this recording session
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
                 if (isActive) { // Ensure coroutine scope is still active
                    withContext(Dispatchers.Main) { // If onRecordingStopped updates UI
                        onRecordingStopped()
                    }
                }
            }
        }
    }

    // --- Recorder.RecorderListener Implementation ---
    override fun onUpdateReceived(message: String?) {
        message?.let {
            Log.d(TAG, "Recorder Update: $it")
            // You could use specific messages like MSG_RECORDING_DONE to trigger onRecordingStopped
            // if stopRecording() itself doesn't robustly signal completion for the UI.
            // For now, stopRecording() explicitly calls onRecordingStopped.
            if (it == Recorder.MSG_RECORDING_DONE) {
                // This indicates the recorder has finished its file operations.
                // The stopRecording method already calls onRecordingStopped, so this might be redundant
                // or could be a safeguard.
            }
        }
    }

    override fun onDataReceived(samples: FloatArray?) {
        if (!isEngineInitialized || samples == null) {
            // Log.v(TAG, "Engine not init or samples null in onDataReceived") // Can be noisy
            return
        }

        transcriptionJob = scope.launch { // Launch new job for each data packet
            try {
                val transcribedChunk = whisperEngine.transcribeBuffer(samples)
                if (transcribedChunk != null && transcribedChunk.isNotBlank()) {
                    // Update transcription on the main thread if it affects UI directly
                    withContext(Dispatchers.Main) {
                        currentTranscription += transcribedChunk // Append chunk
                        onTranscriptionUpdate(currentTranscription)
                        lastSpeechTimeMillis = System.currentTimeMillis() // Update last speech time
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Transcription error: ${e.message}", e)
                // onError("Transcription failed.") // Can be too noisy; log for now
            }
        }
    }

    fun release() {
        Log.d(TAG, "Releasing AudioHandler resources...")
        scope.launch {
            silenceJob?.cancel()
            transcriptionJob?.cancel()
            if (recorder.isInProgress) {
                recorder.stop()
            }
            // No explicit recorder.release() method in Recorder.java.
            // WhisperEngine deinitialization
            if (isEngineInitialized) {
                try {
                    whisperEngine.deinitialize()
                    isEngineInitialized = false
                    Log.d(TAG, "WhisperEngine deinitialized.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error during WhisperEngine deinitialization: ${e.message}", e)
                }
            }
            isHandlerReady = false
        }.invokeOnCompletion {
            scope.cancel() // Cancel the scope itself after all cleanup jobs complete
            Log.d(TAG, "AudioHandler scope cancelled.")
        }
    }
}
