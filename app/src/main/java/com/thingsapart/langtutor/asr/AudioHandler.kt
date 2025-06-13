package com.thingsapart.langtutor.asr

import android.content.Context
import android.util.Log
import com.whispertflite.asr.Recorder
import com.whispertflite.engine.WhisperEngine
import kotlinx.coroutines.*

class AudioHandler(
    private val context: Context,
    private val modelPath: String,
    private val onTranscriptionUpdate: (String) -> Unit,
    private val onRecordingStopped: () -> Unit,
    private val onError: (String) -> Unit,
    private val onSilenceDetected: () -> Unit // Added callback
) {
    private var whisperEngine: WhisperEngine? = null
    private var recorder: Recorder? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var transcriptionJob: Job? = null
    private var currentTranscription: String = ""

    private var lastSpeechTimeMillis: Long = 0 // Added state for silence detection
    private var silenceJob: Job? = null // Added state for silence detection
    private val SILENCE_THRESHOLD_MS = 5000L // Added state for silence detection

    private val vocabPath: String? = null
    private val melFiltersPath: String? = null
    private val isMultilingual = false

    init {
        scope.launch {
            try {
                Log.d("AudioHandler", "Initializing WhisperEngine with model: $modelPath")
                whisperEngine = WhisperEngine(modelPath, vocabPath, melFiltersPath, isMultilingual)
                Log.d("AudioHandler", "WhisperEngine initialized.")
                recorder = Recorder { audioData, _ -> processAudioData(audioData) }
                Log.d("AudioHandler", "Recorder initialized.")
            } catch (e: Exception) {
                Log.e("AudioHandler", "Error initializing: ${e.message}", e)
                onError("Initialization failed: ${e.message}")
                whisperEngine = null; recorder = null
            }
        }
    }

    fun startRecording() {
        if (whisperEngine == null || recorder == null) {
            onError("Audio system not ready."); return
        }
        if (recorder?.isRecording == true) { Log.d("AudioHandler", "Already recording."); return }
        transcriptionJob = scope.launch {
            try {
                currentTranscription = ""; onTranscriptionUpdate("")
                lastSpeechTimeMillis = System.currentTimeMillis() // Reset last speech time
                recorder?.start(); Log.d("AudioHandler", "Recording started.")

                // Launch silence detection job
                silenceJob?.cancel() // Cancel previous job if any
                silenceJob = scope.launch {
                    while (isActive && recorder?.isRecording == true) {
                        delay(500) // Check every 500ms
                        if (recorder?.isRecording == true && System.currentTimeMillis() - lastSpeechTimeMillis > SILENCE_THRESHOLD_MS) {
                            if (currentTranscription.isNotBlank()) { // Only trigger if there's something to send
                                Log.d("AudioHandler", "Silence detected, triggering onSilenceDetected.")
                                onSilenceDetected()
                            } else {
                                Log.d("AudioHandler", "Silence detected, but no transcription. Not triggering send.")
                                // Optionally, could call stopRecording here if desired even without transcription.
                                // For now, relies on onSilenceDetected in ChatScreen to stop.
                            }
                            break // Stop silence detection for this recording session after it's detected once
                        }
                    }
                    Log.d("AudioHandler", "Silence detection job ended.")
                }
            } catch (e: Exception) {
                Log.e("AudioHandler", "Error starting recording: ${e.message}", e)
                onError("Start recording failed: ${e.message}")
            }
        }
    }

    private fun processAudioData(audioData: FloatArray) {
        scope.launch {
            try {
                val transcribedChunk = whisperEngine?.transcribe(audioData)
                if (transcribedChunk != null && transcribedChunk.isNotBlank()) {
                    currentTranscription += transcribedChunk
                    onTranscriptionUpdate(currentTranscription)
                    lastSpeechTimeMillis = System.currentTimeMillis() // Update last speech time
                }
            } catch (e: Exception) { Log.e("AudioHandler", "Transcription error: ${e.message}") }
        }
    }

    fun stopRecording() {
        silenceJob?.cancel(); silenceJob = null // Cancel silence detection job
        if (recorder?.isRecording == false && (transcriptionJob == null || transcriptionJob?.isCompleted == true)) {
            Log.d("AudioHandler", "Effectively stopped or already stopping.");
            // onRecordingStopped might be called multiple times if silence detection also calls stop.
            // Ensure onRecordingStopped is robust or use a flag. For now, it's okay.
            if (recorder?.isRecording == false) onRecordingStopped() // Call if not already stopped
            return
        }
        scope.launch {
            try {
                if (recorder?.isRecording == true) {
                    recorder?.stop(); Log.d("AudioHandler", "Recorder stopped.")
                }
                transcriptionJob?.cancelAndJoin(); transcriptionJob = null
            } catch (e: Exception) {
                Log.e("AudioHandler", "Error stopping: ${e.message}", e)
                onError("Stop recording failed: ${e.message}")
            } finally {
                onRecordingStopped(); Log.d("AudioHandler", "onRecordingStopped invoked from stopRecording.")
            }
        }
    }

    fun release() {
        silenceJob?.cancel(); silenceJob = null // Cancel silence job
        scope.launch {
            try { recorder?.release() } catch (e: Exception) { Log.e("AudioHandler", "Err release recorder", e) }
            try { whisperEngine?.release() } catch (e: Exception) { Log.e("AudioHandler", "Err release engine", e) }
            recorder = null; whisperEngine = null; scope.cancel() // Cancel the scope itself
            Log.d("AudioHandler", "AudioHandler released.")
        }
    }
}
