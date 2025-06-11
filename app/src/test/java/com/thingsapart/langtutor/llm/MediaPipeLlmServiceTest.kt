package com.thingsapart.langtutor.llm

import android.content.Context
import android.util.Log
import com.example.languageapp.llm.* // Import all from your llm package
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.*
import java.io.File
import java.io.IOException

@ExperimentalCoroutinesApi
@RunWith(MockitoJUnitRunner::class) // Essential for Mockito annotations like @Mock
class MediaPipeLlmServiceTest {

    private lateinit var testDispatcher: TestCoroutineDispatcher
    private lateinit var testScope: TestScope

    @Mock private lateinit var mockContext: Context
    @Mock private lateinit var mockModelDownloader: ModelDownloader
    @Mock private lateinit var mockLlmInference: LlmInference
    @Mock private lateinit var mockLlmSession: LlmInferenceSession
    @Mock private lateinit var mockModelFile: File
    @Mock private lateinit var mockLlmInferenceBuilder: LlmInference.LlmInferenceOptions.Builder

    // For capturing lambda in downloadModel
    @Captor private lateinit var progressCallbackCaptor: ArgumentCaptor<(Float) -> Unit>

    private lateinit var service: MediaPipeLlmService
    private val testModelConfig = ModelManager.GEMMA_2B_IT_CPU.copy(modelName = "Test Gemma")

    // Static mocks
    private lateinit var mockedLog: MockedStatic<Log>
    private lateinit var mockedLlmInference: MockedStatic<LlmInference>
    // ModelManager is an object, so its methods are like static calls.
    // If we can't use mockStatic for it, we'll have to test around it for some parts.
    // For this test, we will mock the interactions that ModelManager.checkModelExists and
    // ModelManager.getLocalModelFile would have with the file system or context.

    @Before
    fun setUp() {
        testDispatcher = TestCoroutineDispatcher()
        testScope = TestScope(testDispatcher)

        // Mock Android Log static methods
        mockedLog = Mockito.mockStatic(Log::class.java)

        // Mock LlmInference static methods (especially createFromOptions)
        mockedLlmInference = Mockito.mockStatic(LlmInference::class.java)
        whenever(LlmInference.createFromOptions(any(), any())).thenReturn(mockLlmInference)

        // Mock the builder chain for LlmInferenceOptions
        whenever(LlmInference.LlmInferenceOptions.builder()).thenReturn(mockLlmInferenceBuilder)
        whenever(mockLlmInferenceBuilder.setModelPath(any())).thenReturn(mockLlmInferenceBuilder)
        whenever(mockLlmInferenceBuilder.setPreferredBackend(any())).thenReturn(mockLlmInferenceBuilder)
        whenever(mockLlmInferenceBuilder.build()).thenReturn(mock()) // Return a dummy options object

        // Mock LlmInference instance methods
        whenever(mockLlmInference.createSession()).thenReturn(mockLlmSession)
        // Mock LlmInferenceSession instance methods (can be more specific in tests)
        whenever(mockLlmSession.configure(any(), any(), any(), any())).thenReturn(Unit)


        // Setup for ModelManager interactions
        val tempDir = File(System.getProperty("java.io.tmpdir"), "llm_test_files_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        whenever(mockContext.filesDir).thenReturn(tempDir)
        whenever(mockModelFile.absolutePath).thenReturn(File(tempDir, testModelConfig.internalModelId).absolutePath)
        whenever(mockModelFile.exists()).thenReturn(false) // Default: model does not exist

        // Service initialization
        service = MediaPipeLlmService(mockContext, testModelConfig, mockModelDownloader)
    }

    @After
    fun tearDown() {
        testDispatcher.cleanupTestCoroutines()
        mockedLog.close()
        mockedLlmInference.close()
        // Clean up temp dir if necessary
        mockContext.filesDir.deleteRecursively()
    }

    @Test
    fun `initialize when model already exists, creates engine and session, state becomes Ready`() = testScope.runTest {
        whenever(mockModelFile.exists()).thenReturn(true) // Model exists

        val states = mutableListOf<LlmServiceState>()
        val job = launch { service.serviceState.toList(states) }

        service.initialize()
        advanceUntilIdle() // Process coroutines

        assertEquals(LlmServiceState.Idle, states[0])
        assertEquals(LlmServiceState.Initializing, states[1])
        assertTrue("Final state should be Ready", states.last() is LlmServiceState.Ready)

        verify(mockModelDownloader, never()).downloadModel(any(), any(), any())
        verify(mockLlmInference).createSession()
        verify(mockLlmSession).configure(
            temperature = testModelConfig.temperature,
            topK = testModelConfig.topK,
            topP = testModelConfig.topP,
            maxTokens = testModelConfig.maxTokens
        )
        job.cancel()
    }

    @Test
    fun `initialize when model needs download and succeeds, state transitions correctly to Ready`() = testScope.runTest {
        whenever(mockModelFile.exists()).thenReturn(false) // Model needs download
        whenever(mockModelDownloader.downloadModel(eq(mockContext), eq(testModelConfig), capture(progressCallbackCaptor)))
            .thenAnswer {
                // Simulate progress
                progressCallbackCaptor.value.invoke(0f)
                progressCallbackCaptor.value.invoke(50f)
                progressCallbackCaptor.value.invoke(100f)
                Result.success(mockModelFile)
            }

        val states = mutableListOf<LlmServiceState>()
        val job = launch { service.serviceState.toList(states) }

        service.initialize()
        advanceUntilIdle()

        assertEquals(LlmServiceState.Idle, states[0])
        assertEquals(LlmServiceState.Initializing, states[1])
        assertEquals(LlmServiceState.Downloading(testModelConfig, 0f), states[2])
        assertEquals(LlmServiceState.Downloading(testModelConfig, 50f), states[3])
        assertEquals(LlmServiceState.Downloading(testModelConfig, 100f), states[4])
        assertTrue("Final state should be Ready", states.last() is LlmServiceState.Ready)

        verify(mockModelDownloader).downloadModel(eq(mockContext), eq(testModelConfig), any())
        verify(mockLlmInference).createSession()
        job.cancel()
    }

    @Test
    fun `initialize when model download fails, state becomes Error`() = testScope.runTest {
        val exception = IOException("Download failed")
        whenever(mockModelFile.exists()).thenReturn(false)
        whenever(mockModelDownloader.downloadModel(any(), any(), any())).thenReturn(Result.failure(exception))

        val states = mutableListOf<LlmServiceState>()
        val job = launch { service.serviceState.toList(states) }

        service.initialize()
        advanceUntilIdle()

        val errorState = states.last() as LlmServiceState.Error
        assertEquals("Failed to download model ${testModelConfig.modelName}: ${exception.message}", errorState.message)
        assertEquals(testModelConfig, errorState.modelBeingProcessed)
        job.cancel()
    }

    @Test
    fun `initialize when LlmInference creation fails, state becomes Error`() = testScope.runTest {
        val exception = RuntimeException("Failed to create LlmInference")
        whenever(mockModelFile.exists()).thenReturn(true)
        // This is where proper static mocking of LlmInference.createFromOptions is vital.
        // The @Before setup mocks the static call. Here we make it throw.
        whenever(LlmInference.createFromOptions(any(), any())).thenThrow(exception)

        val states = mutableListOf<LlmServiceState>()
        val job = launch { service.serviceState.toList(states) }

        service.initialize()
        advanceUntilIdle()

        val errorState = states.last() as LlmServiceState.Error
        assertEquals("Failed to initialize MediaPipe LLM engine or session for ${testModelConfig.modelName}: ${exception.message}", errorState.message)
        job.cancel()
    }

    @Test
    fun `initialize when LlmInferenceSession creation fails, state becomes Error and engine closed`() = testScope.runTest {
        val exception = RuntimeException("Session creation failed")
        whenever(mockModelFile.exists()).thenReturn(true)
        // LlmInference.createFromOptions returns mockLlmInference successfully
        whenever(mockLlmInference.createSession()).thenThrow(exception) // Session creation fails

        val states = mutableListOf<LlmServiceState>()
        val job = launch { service.serviceState.toList(states) }

        service.initialize()
        advanceUntilIdle()

        val errorState = states.last() as LlmServiceState.Error
        assertEquals("Failed to initialize MediaPipe LLM engine or session for ${testModelConfig.modelName}: ${exception.message}", errorState.message)
        verify(mockLlmInference).close() // Verify engine is closed
        job.cancel()
    }


    @Test
    fun `generateResponse when Ready, invokes session_generateResponseAsync`() = testScope.runTest {
        // Make service ready
        whenever(mockModelFile.exists()).thenReturn(true)
        service.initialize()
        advanceUntilIdle()
        assertTrue(service.serviceState.value is LlmServiceState.Ready)

        val prompt = "Hello"
        val conversationId = "conv1"
        val targetLanguage = "en"
        val expectedFullPrompt = "User: $prompt\nAI:"

        // Mock the session's async response
        doAnswer { invocation ->
            val listener = invocation.getArgument<ProgressListener>(1)
            listener.onResult(" World", false)
            listener.onResult("!", true)
            null // For Unit return type
        }.whenever(mockLlmSession).generateResponseAsync(eq(expectedFullPrompt), any())


        val responseFlow = service.generateResponse(prompt, conversationId, targetLanguage)
        val results = responseFlow.toList()

        assertEquals(listOf(" World", "!"), results)
        verify(mockLlmSession).generateResponseAsync(eq(expectedFullPrompt), any())
    }

    @Test
    fun `generateResponse when not Ready, returns flow with IllegalStateException`() = testScope.runTest {
        // State is Idle by default
        val responseFlow = service.generateResponse("Hi", "id", "en")
        try {
            responseFlow.collect()
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("LlmService is not ready or session is null"))
        }
    }

    @Test
    fun `getInitialGreeting when Ready, calls generateResponse and collects results`() = testScope.runTest {
        whenever(mockModelFile.exists()).thenReturn(true)
        service.initialize()
        advanceUntilIdle() // Ensure initialize completes and sets state to Ready

        val topic = "Test Topic"
        val targetLanguage = "en"
        val expectedGreetingPrompt = "Generate a friendly, engaging opening message for a conversation about '$topic' in $targetLanguage."
        val expectedFullPromptForGreeting = "User: $expectedGreetingPrompt\nAI:"

        // Mock the session's async response for this specific greeting prompt
        doAnswer { invocation ->
            val listener = invocation.getArgument<ProgressListener>(1)
            listener.onResult("Hello! Let's talk about $topic.", false)
            listener.onResult(" It's interesting.", true)
            null
        }.whenever(mockLlmSession).generateResponseAsync(eq(expectedFullPromptForGreeting), any())

        val greeting = service.getInitialGreeting(topic, targetLanguage)
        assertEquals("Hello! Let's talk about $topic. It's interesting.", greeting)
    }


    @Test
    fun `resetSession when Ready, closes old session and creates new one`() = testScope.runTest {
        // Initial setup to Ready state
        whenever(mockModelFile.exists()).thenReturn(true)
        service.initialize()
        advanceUntilIdle()
        assertTrue(service.serviceState.value is LlmServiceState.Ready)
        val initialSession = mockLlmSession // Keep ref to the first session mock

        // Setup for new session creation
        val newMockSession: LlmInferenceSession = mock()
        whenever(mockLlmInference.createSession()).thenReturn(newMockSession) // Next call to createSession returns new mock

        service.resetSession()
        advanceUntilIdle()

        verify(initialSession).close() // Verify old session was closed
        verify(newMockSession).configure(any(), any(), any(), any()) // Verify new session was configured
        assertTrue(service.serviceState.value is LlmServiceState.Ready)
    }

    @Test
    fun `close transitions to Idle and closes session and engine`() = testScope.runTest {
        // Initial setup to Ready state
        whenever(mockModelFile.exists()).thenReturn(true)
        service.initialize()
        advanceUntilIdle()
        assertTrue(service.serviceState.value is LlmServiceState.Ready)

        service.close()
        advanceUntilIdle()

        assertEquals(LlmServiceState.Idle, service.serviceState.value)
        verify(mockLlmSession).close()
        verify(mockLlmInference).close()
    }
}
