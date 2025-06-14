package com.thingsapart.langtutor.ui.screens

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // Ensure this import is present
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons // Ensure Icons is imported generally
import androidx.compose.material.icons.filled.Mic // Added
import androidx.compose.material.icons.filled.MoreHoriz // Add if not present
import androidx.compose.material.icons.filled.GraphicEq // Add if not present
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop // Import for Stop icon
import androidx.compose.material.icons.filled.Sync // Add this import
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue // Added
import androidx.compose.runtime.mutableStateOf // Added
import androidx.compose.runtime.remember // Added
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import android.widget.Toast // Added
import androidx.compose.runtime.DisposableEffect // Added
import com.thingsapart.langtutor.asr.AudioHandler // Added
// import com.thingsapart.langtutor.llm.ModelManager // Already imported
import com.thingsapart.langtutor.llm.ModelDownloader // Added
import android.Manifest // Added
import android.content.pm.PackageManager // Added
import androidx.activity.compose.rememberLauncherForActivityResult // Added
import androidx.activity.result.contract.ActivityResultContracts // Added
import androidx.core.content.ContextCompat // Added
// import android.util.Log // Already imported
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thingsapart.langtutor.data.AppDatabase
import com.thingsapart.langtutor.data.UserSettingsRepository
// import com.thingsapart.langtutor.data.dao.ChatDao // Not directly used in this file after repository pattern
import com.thingsapart.langtutor.data.model.ChatConversationEntity
import com.thingsapart.langtutor.data.model.ChatMessageEntity
import com.thingsapart.langtutor.llm.LlmService // Interface
import com.thingsapart.langtutor.llm.LlmServiceState
import com.thingsapart.langtutor.llm.ModelManager // Needed for FakeLlmService example state
// import com.thingsapart.langtutor.llm.MediaPipeLlmService // No longer directly needed if AppNav passes LlmService
import com.thingsapart.langtutor.ui.components.ChatMessageBubble
import com.thingsapart.langtutor.ui.components.MetallicPanelGradientBackground // New import
import com.thingsapart.langtutor.ui.components.ModelDownloadDialog
import com.thingsapart.langtutor.ui.components.ModelDownloadDialogState
import com.thingsapart.langtutor.ui.theme.AiBubbleColor
import com.thingsapart.langtutor.ui.theme.SomeDarkColorForText
import com.thingsapart.langtutor.ui.theme.UserBubbleColor
import kotlinx.coroutines.delay
import com.thingsapart.langtutor.ui.theme.LangTutorAppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job // Import for Job
import kotlinx.coroutines.Dispatchers // Added import
import java.util.UUID

class FakeLlmService(initialState: LlmServiceState = LlmServiceState.Ready) : LlmService {
    override val serviceState = MutableStateFlow(initialState)

    override suspend fun initialize() {
        serviceState.value = LlmServiceState.Initializing
        delay(100) // Simulate some work
        serviceState.value = LlmServiceState.Ready
        Log.d("FakeLlmService", "Fake initialized")
    }

    override fun generateResponse(prompt: String, conversationId: String, targetLanguage: String): Flow<String> {
        Log.d("FakeLlmService", "Fake generateResponse called with prompt: $prompt")
        return flow {
            emit("This is a fake response to: ")
            delay(50)
            emit(prompt.take(50) + "...")
        }
    }

    override suspend fun getInitialGreeting(topic: String, targetLanguage: String): String {
        Log.d("FakeLlmService", "Fake getInitialGreeting for topic: $topic")
        return "Hello! Let's talk about $topic in $targetLanguage. (Fake)"
    }

    override fun resetSession() { // Changed signature
        serviceState.value = LlmServiceState.Idle // Corrected: Use public serviceState
        Log.d("FakeLlmService", "Fake session reset called (now synchronous)")
        // Optionally, simulate re-initialization if needed for preview state
        // _serviceState.value = LlmServiceState.Initializing
        // _serviceState.value = LlmServiceState.Ready
    }

    override fun close() {
        serviceState.value = LlmServiceState.Idle
        Log.d("FakeLlmService", "Fake closed")
    }
}

@Composable
fun ChatScreen(
    chatId: String?,
    languageCode: String? = null,
    topicId: String? = null,
    chatRepository: com.thingsapart.langtutor.data.ChatRepository,
    userSettingsRepository: UserSettingsRepository,
    llmService: LlmService // Changed to LlmService interface
) {
    val context = LocalContext.current // Added
    var hasRecordAudioPermission by remember { // Added
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult( // Added
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasRecordAudioPermission = isGranted
            if (isGranted) {
                Log.d("ChatScreen", "RECORD_AUDIO permission granted.")
            } else {
                Log.d("ChatScreen", "RECORD_AUDIO permission denied.")
                // Optionally, show a snackbar or dialog explaining why permission is needed.
            }
        }
    )

    val requestAudioPermission: () -> Unit = { // Added
        if (!hasRecordAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            // Permission already granted, proceed with recording logic (to be added)
            Log.d("ChatScreen", "Audio permission already granted. Ready to record.")
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var currentChatId by remember { mutableStateOf(chatId) }
    var conversationTitle by remember { mutableStateOf("Chat") }
    var isRecording by remember { mutableStateOf(false) }
    var isSoundBeingDetected by remember { mutableStateOf(false) } // New state
    var isTranscribing by remember { mutableStateOf(false) }       // Existing state
    var userIntentRecording by remember { mutableStateOf(false) } // New state

    var audioHandler by remember { mutableStateOf<AudioHandler?>(null) }
    var asrComponentsReady by remember { mutableStateOf(false) } // Renamed from asrModelExists
    val modelDownloader = remember { ModelDownloader() }
    var asrDownloadState by remember { mutableStateOf<ModelDownloadDialogState?>(null) }

    // Define contrasting colors (these could also come from a Theme extension)
    val textColorOnGradient = SomeDarkColorForText
    val userBubbleBackgroundColor = UserBubbleColor
    val aiBubbleBackgroundColor = AiBubbleColor
    val textOnUserBubbleColor = SomeDarkColorForText
    val textOnAiBubbleColor = SomeDarkColorForText

    val messagesFlow: Flow<List<ChatMessageEntity>> = remember(currentChatId) {
        currentChatId?.let {
            chatRepository.getMessagesForConversation(it)
        } ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }
    val messages by messagesFlow.collectAsState(initial = emptyList())
    var inputText by remember { mutableStateOf("") }

    var isLlmGenerating by remember { mutableStateOf(false) }
    var llmResponseJob by remember { mutableStateOf<Job?>(null) }

    val llmState by llmService.serviceState.collectAsStateWithLifecycle()
    var downloadDialogController by remember { mutableStateOf(ModelDownloadDialogState(showDialog = false)) }

    fun sendMessage() {
        // Use llmResponseJob to manage isLlmGenerating
        llmResponseJob = coroutineScope.launch {
            val originalText = inputText
            try {
                isLlmGenerating = true
                inputText = ""
                chatRepository.sendMessage(
                    currentChatId!!, ChatMessageEntity(
                        conversationId = currentChatId!!,
                        text = originalText,
                        timestamp = System.currentTimeMillis(),
                        isUserMessage = true
                    )
                )
            } catch (e: Exception) {
                Log.e("ChatScreen", "Error during auto-send on silence: ${e.message}", e)
                inputText = originalText
                // Optionally, do not clear inputText if send failed, allowing user to see/resend.
            } finally {
                isLlmGenerating = false
            }
        }
    }

    LaunchedEffect(isLlmGenerating) {
        if (!isLlmGenerating) { // LLM has just finished generating
            Log.d("ChatScreen", "LLM finished generating. Checking userIntentRecording: $userIntentRecording")
            if (userIntentRecording && audioHandler != null && !isRecording) {
                // If user intended to record, LLM is done, and we are not already recording
                Log.d("ChatScreen", "LLM finished and userIntentRecording is true. Restarting recording.")
                isRecording = true // Set UI state to recording
                inputText = "" // Clear any potentially stale input text from previous turn
                audioHandler?.startRecording()
            }
        } else {
            // LLM has started generating.
            // If currently recording (e.g. user spoke, transcription happened, then LLM started),
            // and if onTranscriptionProcessStateChange didn't already stop it,
            // this could be another place to ensure mic is off.
            // However, the primary stop should occur when transcription starts.
            // This block can be used for logging or specific actions when LLM generation begins.
            Log.d("ChatScreen", "LLM started generating. isRecording: $isRecording, isTranscribing: $isTranscribing")
            if (isRecording && !isTranscribing) {
                // This case implies LLM started, but transcription didn't stop the mic (unlikely with Part B)
                // Or if user somehow bypasses transcription and directly triggers LLM while mic is on.
                Log.w("ChatScreen", "LLM started generating, but mic was still on and not in transcribing state. Stopping recording.")
                audioHandler?.stopRecording()
                // isRecording will be set to false by onRecordingStopped.
                // userIntentRecording should still be true if the user was recording.
            }
        }
    }

    // Check for ASR model and vocab existence and initiate download if needed
    LaunchedEffect(Unit) {
        val asrConfig = ModelManager.WHISPER_DEFAULT_MODEL
        val modelFileExists = ModelManager.checkAsrModelExists(context, asrConfig)
        val vocabFileExists = asrConfig.vocabUrl?.let { ModelManager.checkAsrVocabExists(context, asrConfig) } ?: true // Vocab exists if no vocabUrl

        if (modelFileExists && vocabFileExists) {
            Log.i("ChatScreen", "ASR model and vocab (if req) already exist.")
            asrComponentsReady = true
            asrDownloadState = null // Ensure dialog is hidden
            return@LaunchedEffect
        }

        // Only proceed if components aren't ready and dialog isn't already active from a previous attempt in this session
        if (asrDownloadState != null && asrDownloadState?.showDialog == true) {
             Log.i("ChatScreen", "ASR download dialog already active or recently handled.")
             return@LaunchedEffect
        }

        coroutineScope.launch { // Use existing coroutineScope
            // Stage 1: Download ASR Model File
            if (!modelFileExists) {
                Log.i("ChatScreen", "ASR model file not found. Initiating download.")
                val dialogModelName = "ASR Model" + if (asrConfig.vocabUrl != null) " (1/2)" else ""
                asrDownloadState = ModelDownloadDialogState(
                    showDialog = true, modelName = dialogModelName, progress = 0f
                )
                val modelDownloadResult = modelDownloader.downloadAsrModel(context, asrConfig) { progress ->
                    asrDownloadState = asrDownloadState?.copy(progress = progress)
                }

                if (modelDownloadResult.isFailure) {
                    val errorMsg = modelDownloadResult.exceptionOrNull()?.message ?: "Unknown ASR model download error"
                    asrDownloadState = asrDownloadState?.copy(errorMessage = errorMsg, progress = 0f)
                    Log.e("ChatScreen", "ASR model download failed: $errorMsg")
                    return@launch // Stop further processing
                }
                // Don't mark complete yet if vocab needs downloading
                 asrDownloadState = asrDownloadState?.copy(progress = 100f) // Show 100% for model part
            }

            // Stage 2: Download ASR Vocab File (if specified and not already existing)
            if (asrConfig.vocabUrl != null && !(ModelManager.checkAsrVocabExists(context, asrConfig))) {
                Log.i("ChatScreen", "ASR vocab file not found. Initiating download.")
                val dialogModelName = "ASR Vocab" + if (asrConfig.vocabUrl != null) " (2/2)" else "" // Or just "ASR Vocab"
                 asrDownloadState = ModelDownloadDialogState( // Reset dialog for vocab
                    showDialog = true, modelName = dialogModelName, progress = 0f
                )
                val vocabDownloadResult = modelDownloader.downloadAsrVocab(context, asrConfig) { progress ->
                    asrDownloadState = asrDownloadState?.copy(progress = progress)
                }

                if (vocabDownloadResult.isFailure) {
                    val errorMsg = vocabDownloadResult.exceptionOrNull()?.message ?: "Unknown ASR vocab download error"
                    asrDownloadState = asrDownloadState?.copy(errorMessage = errorMsg, progress = 0f)
                    Log.e("ChatScreen", "ASR vocab download failed: $errorMsg")
                    return@launch // Stop further processing
                }
                 asrDownloadState = asrDownloadState?.copy(progress = 100f) // Show 100% for vocab part
            }

            // All downloads successful (or files already existed)
            Log.i("ChatScreen", "All required ASR components are ready.")
            asrDownloadState = asrDownloadState?.copy(isComplete = true, progress = 100f, modelName = "ASR Components") // Generic completion
            asrComponentsReady = true
        }
    }

    // Initialize AudioHandler
    LaunchedEffect(hasRecordAudioPermission, asrComponentsReady) { // Changed asrModelExists to asrComponentsReady
        if (hasRecordAudioPermission && asrComponentsReady) {
            if (audioHandler == null) {
                val modelPath = ModelManager.getLocalAsrModelPath(context, ModelManager.WHISPER_DEFAULT_MODEL)
                val asrConfig = ModelManager.WHISPER_DEFAULT_MODEL

                // Determine the vocabulary path to pass
                val finalVocabPath = if (asrConfig.vocabUrl != null && asrConfig.vocabFileName != null) {
                    ModelManager.getLocalAsrVocabPath(context, asrConfig)
                        ?: modelPath // Fallback to modelPath if vocabPath is somehow null despite URL (should not happen if downloaded)
                } else {
                    modelPath // Fallback if no vocabUrl is defined in config (maintains previous NPE workaround)
                }

                Log.d("ChatScreen", "Initializing AudioHandler with model: $modelPath, finalVocabPath: $finalVocabPath")
                audioHandler = AudioHandler(
                    context = context,
                    modelPath = modelPath,
                    vocabPath = finalVocabPath,
                    isMultilingual = ModelManager.WHISPER_DEFAULT_MODEL.isMultilingual,
                    onTranscriptionUpdate = { transcription ->
                        inputText = transcription
                    },
                    onRecordingStopped = {
                        isRecording = false // Ensure isRecording state is updated
                        // The problematic line 'if (isRecording) { audioHandler?.startRecording() }' should be removed.
                        Log.d("ChatScreen", "AudioHandler: Recording stopped callback. isRecording set to false.")
                    },
                    onError = { errorMessage ->
                        Log.e("ChatScreen", "AudioHandler Error: $errorMessage")
                        coroutineScope.launch(Dispatchers.Main) { // Added Dispatchers.Main
                            Toast.makeText(context, "ASR Error: $errorMessage", Toast.LENGTH_LONG).show()
                        }
                        if (isRecording) {
                            // This part itself might also need to be on Main if stopRecording() or isRecording mutation affects UI directly
                            // However, state changes like isRecording = false are generally safe if they trigger recomposition.
                            // audioHandler.stopRecording() internally uses its own scope, which is IO, but its callbacks (onRecordingStopped)
                            // should also dispatch to Main if they update UI directly.
                            // For now, only the Toast is explicitly moved.
                            audioHandler?.stopRecording()
                        } else {
                            isRecording = false
                        }
                        isTranscribing = false // Also ensure transcribing state is reset on error
                        isSoundBeingDetected = false // Reset sound detection on error
                    },
                    onSilenceDetected = {
                        // This callback is triggered by AudioHandler when its VAD detects silence.
                        // AudioHandler itself does not stop recording.
                        // We use the current content of `inputText`, which is from the last *full* ASR processing run.
                        val textToSend = inputText // Capture current inputText
                        if (textToSend.isNotBlank()) {
                            Log.d("ChatScreen", "Silence detected by AudioHandler. Auto-sending current inputText: '$textToSend'")
                            val currentId = currentChatId
                            if (currentId != null) {
                                // Potentially remove or comment out this sendMessage call later if the new mechanism is preferred
                                // sendMessage()
                                Log.d("ChatScreen", "onSilenceDetected: Message sending is now primarily handled by onTranscriptionCompleteAndSend.")
                            }
                        } else {
                            Log.d("ChatScreen", "Silence detected by AudioHandler, but inputText is blank. No action.")
                        }
                        // CRUCIALLY: Do NOT call audioHandler?.stopRecording() here.
                        // Recording continues in AudioHandler as per user request.
                    },
                    onTranscriptionCompleteAndSend = { transcribedText ->
                        // transcribedText parameter is available if needed, but inputText is also updated.
                        if (transcribedText.isNotBlank()) {
                            Log.d("ChatScreen", "onTranscriptionCompleteAndSend: Received transcription: '$transcribedText'. Sending message.")
                            // Ensure inputText is up-to-date if sendMessage relies on it solely.
                            // inputText = transcribedText // This should already be set by onTranscriptionUpdate
                            val currentId = currentChatId
                            if (currentId != null) {
                                sendMessage() // sendMessage() will use the updated inputText
                            }
                        } else {
                            Log.d("ChatScreen", "onTranscriptionCompleteAndSend: Received blank transcription. No action.")
                        }
                    },
                    // Add the new callbacks here
                    onSpeechActive = { isActive ->
                        isSoundBeingDetected = isActive
                        Log.d("ChatScreen", "onSpeechActive: isActive = $isActive")
                    },
                    onTranscriptionProcessStateChange = { isProcessing ->
                        isTranscribing = isProcessing
                        Log.d("ChatScreen", "onTranscriptionProcessStateChange: isProcessing = $isProcessing")
                        if (isProcessing && isRecording) {
                            // Transcription has started from a live recording session
                            Log.d("ChatScreen", "Transcription started while recording. Stopping audioHandler recording now.")
                            // We don't set isRecording = false here directly.
                            // audioHandler.stopRecording() will trigger onRecordingStopped, which sets isRecording = false.
                            // userIntentRecording remains true because the user didn't manually stop.
                            audioHandler?.stopRecording()
                        }
                    }
                )
            }
        } else {
            // Permission not granted or model doesn't exist, release if already initialized
            audioHandler?.release()
            audioHandler = null
            if (!hasRecordAudioPermission) Log.i("ChatScreen", "AudioHandler waiting: Audio permission not yet granted.")
            if (!asrComponentsReady) Log.i("ChatScreen", "AudioHandler waiting: ASR components not yet ready.") // Updated log
        }
    }

    // Dispose AudioHandler
    DisposableEffect(Unit) { // Added
        onDispose {
            Log.d("ChatScreen", "Disposing AudioHandler.")
            audioHandler?.release()
        }
    }

    LaunchedEffect(llmService) {
        if (llmState is LlmServiceState.Idle || llmState is LlmServiceState.Error) {
            Log.d("ChatScreen", "Attempting to initialize LLM Service from ChatScreen.")
            launch {
                llmService.initialize()
            }
        }
    }

    LaunchedEffect(llmState) {
        Log.d("ChatScreen", "LLM State changed: $llmState")
        when (val currentLlmState = llmState) {
            is LlmServiceState.Downloading -> {
                downloadDialogController = ModelDownloadDialogState(
                    showDialog = true,
                    modelName = currentLlmState.model.modelName, // Updated
                    progress = currentLlmState.progress,
                    isComplete = false,
                    errorMessage = null
                )
            }

            is LlmServiceState.Error -> {
                downloadDialogController = ModelDownloadDialogState(
                    showDialog = true,
                    modelName = currentLlmState.modelBeingProcessed?.modelName, // Updated
                    progress = downloadDialogController.progress,
                    isComplete = false,
                    errorMessage = currentLlmState.message
                )
            }

            LlmServiceState.Ready -> {
                if (downloadDialogController.showDialog && !downloadDialogController.isComplete && downloadDialogController.errorMessage == null) {
                    downloadDialogController =
                        downloadDialogController.copy(isComplete = true, progress = 100f)
                } else if (downloadDialogController.errorMessage != null) {
                    downloadDialogController = downloadDialogController.copy(showDialog = false)
                }
            }

            LlmServiceState.Idle, LlmServiceState.Initializing -> {
                // Handled by other states or initial view
            }
        }
    }

    if (downloadDialogController.showDialog) {
        ModelDownloadDialog(
            state = downloadDialogController,
            onDismissRequest = {
                downloadDialogController = downloadDialogController.copy(showDialog = false)
            },
            onRetry = {
                // The model context (which model to retry) is implicit: it's the one that put the dialog in an error state.
                // We rely on llmService.initialize() to know which model it was processing or to re-evaluate.
                Log.d("ChatScreen", "Retry download for LLM model: ${downloadDialogController.modelName}")
                downloadDialogController =
                    downloadDialogController.copy(errorMessage = null, progress = 0f)
                coroutineScope.launch { llmService.initialize() }
            }
        )
    }

    // ASR Model Download Dialog
    asrDownloadState?.let { state ->
        if (state.showDialog) {
            ModelDownloadDialog(
                state = state,
                onDismissRequest = {
                    asrDownloadState = if (state.isComplete || state.errorMessage != null) {
                        null // Hide dialog if complete or error is acknowledged by closing
                    } else {
                        // Don't dismiss if download is in progress
                        state
                    }
                },
                onRetry = {
                    Log.d("ChatScreen", "Retrying ASR model download.")
                    // Reset state for retry and re-trigger the LaunchedEffect logic
                    // by setting asrDownloadState to null and ensuring model isn't marked as existing yet.
                    // The LaunchedEffect(Unit) should re-check and re-initiate.
                    if (asrDownloadState?.errorMessage != null) { // only if retry is for an error
                        asrComponentsReady = false // Ensure readiness is re-checked
                    }
                    asrDownloadState = null // This will allow the LaunchedEffect(Unit) to re-trigger download
                }
            )
        }
    }

    LaunchedEffect(key1 = chatId, key2 = languageCode, key3 = topicId) {
        if (chatId != null) {
            currentChatId = chatId
            // Fetch title if needed
            chatRepository.getConversationById(chatId)
                .collect { conversation -> // Assuming getConversationById exists in repo and returns Flow<ChatConversationEntity?>
                    conversationTitle = conversation?.conversationTitle ?: "Chat"
                }
        } else if (languageCode != null && topicId != null) {
            // Try to find existing conversation by language and topic
            val existingConversation =
                chatRepository.getConversationByLanguageAndTopic(languageCode, topicId)
                    .firstOrNull() // Use firstOrNull() for one-time check

            if (existingConversation != null) {
                currentChatId = existingConversation.id
                conversationTitle = existingConversation.conversationTitle
                Log.d("ChatScreen", "Found existing conversation: ${existingConversation.id}")
            } else {
                // No existing conversation, create a new one
                val newConversationId = UUID.randomUUID().toString()
                currentChatId = newConversationId
                // Generate a more descriptive title, perhaps based on actual topic name later if available
                // For now, topicId is good.
                conversationTitle = "Chat: $languageCode - $topicId"
                val newConversation = ChatConversationEntity(
                    id = newConversationId,
                    targetLanguageCode = languageCode,
                    topicId = topicId,
                    lastMessage = null, // Will be set by initial greeting
                    lastMessageTimestamp = System.currentTimeMillis(), // Initial timestamp
                    userProfileImageUrl = null, // Placeholder
                    conversationTitle = conversationTitle
                )
                Log.d(
                    "ChatScreen",
                    "Creating new conversation: $newConversationId for $languageCode, $topicId"
                )
                // The startNewConversation method in ChatRepository already handles LLM interaction
                // and initial message.
                // It's important that startNewConversation is robust enough or that LLM readiness is handled.
                // The existing code already has some LLM state checks for sending messages.
                // Consider if LLM must be ready before even attempting to create in DB.
                // For now, assuming repository handles this.
                chatRepository.startNewConversation(newConversation)
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    MetallicPanelGradientBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            // Make Scaffold background transparent to see the gradient
            backgroundColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            conversationTitle,
                            color = textColorOnGradient
                        )
                    }, // Ensure title is readable
                    // Consider making TopAppBar background transparent or semi-transparent
                    backgroundColor = Color.Black.copy(alpha = 0.2f), // Example: semi-transparent
                    elevation = 0.dp // Remove shadow if it looks odd with gradient
                )
            },
            bottomBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "Type a message...",
                                color = textColorOnGradient.copy(alpha = 0.7f)
                            )
                        },
                        colors = TextFieldDefaults.textFieldColors(
                            textColor = textColorOnGradient,
                            backgroundColor = Color.White.copy(alpha = 0.5f), // Semi-transparent background for input
                            cursorColor = textColorOnGradient,
                            focusedIndicatorColor = Color.Transparent, // Optional: hide indicator if design calls for it
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // New Microphone Button
                    IconButton(
                        onClick = {
                            requestAudioPermission() // Keep permission check
                            if (hasRecordAudioPermission) {
                                if (audioHandler != null && asrComponentsReady) {
                                    if (isRecording || isTranscribing) {
                                        // If recording OR transcribing, user wants to stop the whole voice interaction flow
                                        Log.d("ChatScreen", "User action: Stop/Cancel. Was recording: $isRecording, Was transcribing: $isTranscribing")
                                        userIntentRecording = false // Cancel intent to record further
                                        if (isRecording) {
                                            audioHandler?.stopRecording() // Stop active recording if any
                                        }
                                        // isRecording will be set to false by onRecordingStopped callback.
                                        // isTranscribing will be set to false by its own callback when transcription finishes.
                                    } else {
                                        // Not recording & not transcribing (mic icon is shown), so user wants to start
                                        isRecording = true
                                        userIntentRecording = true
                                        inputText = ""
                                        Log.d("ChatScreen", "User action: Starting recording. userIntentRecording = true")
                                        audioHandler?.startRecording()
                                    }
                                } else {
                                    Log.w("ChatScreen", "AudioHandler not ready or ASR model missing.")
                                    Toast.makeText(context, "ASR system not ready.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Log.d("ChatScreen", "Mic clicked, permission pending/denied. Request was launched.")
                                Toast.makeText(context, "Audio permission required.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = llmState is LlmServiceState.Ready && !isLlmGenerating && asrComponentsReady
                    ) {
                        val iconImage = when {
                            isLlmGenerating -> Icons.Filled.Sync // Highest priority: LLM generating
                            isRecording && isTranscribing -> Icons.Filled.MoreHoriz
                            isRecording && isSoundBeingDetected -> Icons.Filled.GraphicEq
                            isRecording -> Icons.Filled.Stop
                            else -> Icons.Filled.Mic
                        }
                        val contentDesc = when {
                            isLlmGenerating -> "AI is thinking..." // Content description for LLM generating
                            isRecording && isTranscribing -> "Transcribing audio..."
                            isRecording && isSoundBeingDetected -> "Sound detected"
                            isRecording -> "Stop recording"
                            else -> "Start recording"
                        }
                        Icon(
                            imageVector = iconImage,
                            contentDescription = contentDesc,
                            tint = textColorOnGradient
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp)) // Spacer between mic and send/stop

                    // Conditional Button: Stop for LLM generation or Send
                    if (isLlmGenerating) {
                        IconButton(
                            onClick = {
                                llmResponseJob?.cancel()
                                isLlmGenerating = false
                                if (isRecording) {
                                    isRecording = false
                                    Log.d("ChatScreen", "LLM Stop also stopped recording.")
                                    audioHandler?.stopRecording() // Stop ASR if LLM is stopped.
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop, // This is for LLM stop
                                contentDescription = "Stop generation",
                                tint = textColorOnGradient
                            )
                        }
                    } else {
                        // Send Button - only shown if not generating LLM response
                        IconButton(
                            onClick = {
                                val currentId = currentChatId
                                if (inputText.isNotBlank() && currentId != null) {
                                    val userMessage = ChatMessageEntity(
                                        conversationId = currentId,
                                        text = inputText,
                                        timestamp = System.currentTimeMillis(),
                                        isUserMessage = true
                                    )
                                    llmResponseJob = coroutineScope.launch {
                                        try {
                                            isLlmGenerating = true
                                            chatRepository.sendMessage(currentId, userMessage)
                                            Log.d("ChatScreen", "SendButton pressed $userMessage")
                                        } finally {
                                            isLlmGenerating = false
                                        }
                                    }
                                    inputText = ""
                                }
                            },
                            enabled = currentChatId != null && llmState is LlmServiceState.Ready
                        ) {
                            Icon(
                                Icons.Filled.Send,
                                contentDescription = "Send message",
                                tint = textColorOnGradient
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 8.dp),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom)
            ) {
                items(messages.reversed(), key = { it.id }) { message ->
                    // Pass bubble and text colors to ChatMessageBubble
                    // This might require ChatMessageBubble to accept these as parameters
                    ChatMessageBubble(
                        messageText = message.text,
                        isUserMessage = message.isUserMessage,
                        bubbleColor = if (message.isUserMessage) userBubbleBackgroundColor else aiBubbleBackgroundColor,
                        textColor = if (message.isUserMessage) textOnUserBubbleColor else textOnAiBubbleColor,
                        showSpeakerIcon = !message.isUserMessage,
                        onSpeakerIconClick = {
                            Log.d("ChatScreen", "Speaker icon clicked for message: ${message.text}")
                        }
                    )
                }
            }
        }
    }
}
// ... existing LaunchedEffects and Dialogs ...
// ModelDownloadDialog might need its own background/theming if it appears over this.

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun ChatScreenPreview_ExistingChat() {
    val context = LocalContext.current
    val previewLlmService: LlmService = FakeLlmService()

    val dummyRepo = com.thingsapart.langtutor.data.ChatRepository(
        AppDatabase.getInstance(context).chatDao(), // Assuming ChatDao is accessible for preview
        previewLlmService
    )
    val dummyUserSettingsRepo = UserSettingsRepository(context)
    LangTutorAppTheme {
        ChatScreen(
            chatId = "previewChatId",
            chatRepository = dummyRepo,
            userSettingsRepository = dummyUserSettingsRepo,
            llmService = previewLlmService
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, name = "New Chat From Topic Preview")
@Composable
fun ChatScreenPreview_NewChatFromTopic() {
    val context = LocalContext.current
    val previewLlmService: LlmService = FakeLlmService(LlmServiceState.Downloading(ModelManager.DEFAULT_MODEL, 50f))

    val dummyRepo = com.thingsapart.langtutor.data.ChatRepository(
        AppDatabase.getInstance(context).chatDao(), // Assuming ChatDao is accessible for preview
        previewLlmService
    )
    val dummyUserSettingsRepo = UserSettingsRepository(context)
    LangTutorAppTheme {
        ChatScreen(
            chatId = null,
            languageCode = "es",
            topicId = "greetings",
            chatRepository = dummyRepo,
            userSettingsRepository = dummyUserSettingsRepo,
            llmService = previewLlmService
        )
    }
}