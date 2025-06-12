// Ensure these imports are present
import android.content.Context
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock // Keep this if you still use @Mock for modelDownloader
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.metadata.MetadataExtractor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.io.ByteArrayInputStream
import java.nio.IntBuffer

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class LiteRtLlmServiceTest {

    private lateinit var context: Context
    private lateinit var service: LiteRtLlmService

    // Using @MockK for ModelDownloader now for consistency with other mocks
    private lateinit var mockModelDownloader: ModelDownloader
    private lateinit var mockInterpreter: Interpreter // This will be the one created by constructor

    private val testDispatcher = StandardTestDispatcher()
    private val ioDispatcher = StandardTestDispatcher() // Not explicitly used yet, but good practice

    private val testModelConfigDefault = LlmModelConfig(
        modelName = "Test Model",
        internalModelId = "test_model.tflite",
        url = "http://example.com/test_model.tflite",
        licenseUrl = "",
        needsAuth = false,
        preferredBackend = ModelBackend.CPU,
        temperature = 0.7f,
        topK = 50,
        topP = 0.9f,
        maxTokens = 10, // Max tokens for generated sequence by model
        padTokenId = 0,
        bosTokenId = 1, // Beginning of sentence
        eosTokenId = 2, // End of sentence
        vocabFileNameInMetadata = "vocab.txt"
    )
    private lateinit var currentTestModelConfig: LlmModelConfig
    private lateinit var modelFile: File
    private lateinit var mockFileByteBuffer: MappedByteBuffer
    private lateinit var mockMetadataExtractor: MetadataExtractor

    @Before
    fun setUp() {
        // MockitoAnnotations.openMocks(this) // Not needed if only using MockK for @Mock fields
        context = RuntimeEnvironment.getApplication()
        Dispatchers.setMain(testDispatcher)

        currentTestModelConfig = testModelConfigDefault.copy() // Use a copy for each test

        modelFile = File(context.filesDir, currentTestModelConfig.internalModelId)
        modelFile.parentFile?.mkdirs()
        modelFile.createNewFile() // Ensure it exists for "model exists" scenarios

        mockModelDownloader = mockk()
        coEvery { mockModelDownloader.downloadModel(any(), any(), any()) } coAnswers {
            val cb = arg<suspend (Float) -> Unit>(2)
            cb(0f); cb(100f)
            Result.success(modelFile)
        }

        mockkStatic(FileUtil::class)
        mockFileByteBuffer = mockk<MappedByteBuffer>(relaxed = true).also {
            every { it.duplicate() } returns it
        }
        every { FileUtil.loadMappedFile(any(), any<String>()) } returns mockFileByteBuffer

        mockkStatic(MetadataExtractor::class)
        mockMetadataExtractor = mockk<MetadataExtractor>(relaxed = true).also {
            every { MetadataExtractor(any<ByteBuffer>()) } returns it
            every { it.hasMetadata() } returns false // Default: no metadata
            every { it.getAssociatedFile(any()) } returns null
        }

        // Key change: Mock the Interpreter constructor
        mockkConstructor(Interpreter::class)
        // Capture the created interpreter instance to verify calls on it
        // 'mockInterpreter' will be assigned to the last Interpreter instance created
        every { constructedWith<Interpreter>().also { mockInterpreter = it } } answers { selfCall ->
            // Configure default behavior for the mocked interpreter
            // Do this carefully, only mock what's essential for most tests
            // Specific tests can override this.
            val mockInputTensor = mockk<Tensor>(relaxed = true)
            every { selfCall.getInputTensor(0) } returns mockInputTensor
            every { mockInputTensor.shape() } returns intArrayOf(1, currentTestModelConfig.maxTokens)


            val mockOutputTensor = mockk<Tensor>(relaxed = true)
            every { selfCall.getOutputTensor(0) } returns mockOutputTensor
            every { mockOutputTensor.shape() } returns intArrayOf(1, currentTestModelConfig.maxTokens)


            // Default run behavior: fill output with pad tokens
            every { selfCall.run(any<IntBuffer>(), any<IntBuffer>()) } answers {
                val outputBuf = arg<IntBuffer>(1)
                while (outputBuf.hasRemaining()) {
                    outputBuf.put(currentTestModelConfig.padTokenId)
                }
                outputBuf.rewind()
            }
            every { selfCall.close() } just Runs // Ensure close() can be called without issue
            selfCall // Proceed with actual constructor call (which is mocked)
        }

        service = LiteRtLlmService(context, currentTestModelConfig, mockModelDownloader)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        modelFile.delete()
        unmockkAll() // Clears all MockK mocks, including static and constructors
    }

    // --- Existing tests from previous subtask (slightly adapted for current mockInterpreter setup) ---
    @Test
    fun `initialize successfully when model exists`() = runTest(testDispatcher) {
        assertTrue("Dummy model file should exist", modelFile.exists())
        service.initialize()
        advanceUntilIdle()
        assertEquals(LlmServiceState.Ready, service.serviceState.value)
        coVerify(exactly = 0) { mockModelDownloader.downloadModel(any(), any(), any()) }
        verify { FileUtil.loadMappedFile(context, modelFile.absolutePath) }
        verify { MetadataExtractor(mockFileByteBuffer) }
        assertNotNull(mockInterpreter) // Verify an interpreter was constructed
    }

    @Test
    fun `initialize successfully and downloads model if not present`() = runTest(testDispatcher) {
        modelFile.delete()
        assertFalse("Model file should not exist before download", modelFile.exists())
        service.initialize()
        advanceUntilIdle()
        assertEquals(LlmServiceState.Ready, service.serviceState.value)
        coVerify(exactly = 1) { mockModelDownloader.downloadModel(context, currentTestModelConfig, any()) }
    }

    @Test
    fun `initialize fails if model download fails`() = runTest(testDispatcher) {
        modelFile.delete()
        val downloadException = IOException("Download failed")
        coEvery { mockModelDownloader.downloadModel(any(), any(), any()) } returns Result.failure(downloadException)
        service.initialize()
        advanceUntilIdle()
        val state = service.serviceState.value
        assertTrue(state is LlmServiceState.Error)
        assertEquals("Failed to download model ${currentTestModelConfig.modelName}: ${downloadException.message}", (state as LlmServiceState.Error).message)
    }

    @Test
    fun `initialize fails if interpreter creation throws`() = runTest(testDispatcher) {
        val interpreterException = RuntimeException("Interpreter creation failed")
        // Make the constructor throw an exception
        every { constructedWith<Interpreter>() } throws interpreterException

        // Re-create service as the constructor mock is set up before service instantiation
        service = LiteRtLlmService(context, currentTestModelConfig, mockModelDownloader)
        service.initialize()
        advanceUntilIdle()

        val state = service.serviceState.value
        assertTrue(state is LlmServiceState.Error)
        assertTrue((state as LlmServiceState.Error).message.contains("Failed to initialize LiteRT engine"))
    }

    @Test
    fun `close sets state to Idle and closes interpreter`() = runTest(testDispatcher) {
        service.initialize() // Initialize to get an interpreter instance
        advanceUntilIdle()
        assertEquals(LlmServiceState.Ready, service.serviceState.value)

        service.close()
        advanceUntilIdle()

        assertEquals(LlmServiceState.Idle, service.serviceState.value)
        verify(exactly = 1) { mockInterpreter.close() } // Verify close on the captured mock
    }

    // --- New and Updated Tests ---
    private fun setupVocabulary(vocab: Map<String, Int>) {
        every { mockMetadataExtractor.hasMetadata() } returns true
        val vocabContent = vocab.keys.joinToString("\n")
        val inputStream = ByteArrayInputStream(vocabContent.toByteArray())
        every { mockMetadataExtractor.getAssociatedFile(currentTestModelConfig.vocabFileNameInMetadata) } returns inputStream
    }

    @Test
    fun `initialize loads vocabulary successfully from metadata`() = runTest(testDispatcher) {
        val vocabulary = mapOf("hello" to 10, "world" to 11)
        setupVocabulary(vocabulary)

        service.initialize()
        advanceUntilIdle()

        assertEquals(LlmServiceState.Ready, service.serviceState.value)
        verify { mockMetadataExtractor.getAssociatedFile(currentTestModelConfig.vocabFileNameInMetadata) }
        // Test actual vocab content by trying to tokenize something known
        // This requires tokenizeText to be accessible or tested via generateResponse
    }

    @Test
    fun `tokenizeText uses bos, eos, pad and unk correctly`() {
        // This test focuses on the tokenization logic itself.
        // Requires access to the vocabularyMap inside the service.
        // For robust testing, LiteRtLlmService.tokenizeText should be public or @VisibleForTesting.
        // Using reflection as a workaround for now.
        val localConfig = currentTestModelConfig.copy(maxTokens = 7, bosTokenId = 1, eosTokenId = 2, padTokenId = 0)
        val serviceForTokenTest = LiteRtLlmService(context, localConfig, mockModelDownloader)

        val vocab = mapOf("hello" to 10, "world" to 11)
        val vocabMapField = serviceForTokenTest.javaClass.getDeclaredField("vocabularyMap")
        vocabMapField.isAccessible = true
        (vocabMapField.get(serviceForTokenTest) as MutableMap<String, Int>).putAll(vocab)

        // "hello world unknown" -> BOS hello world PAD EOS PAD PAD (if EOS is aggressively placed)
        // Or BOS hello world UNK EOS PAD PAD (if UNK is distinct from PAD)
        // Current LiteRtLlmService uses padTokenId for unknown.
        // BOS + hello + world + unknown_token (padId) + EOS + pad + pad
        // [1, 10, 11, 0, 2, 0, 0]
        val tokenIds = serviceForTokenTest.tokenizeTextPublic("hello world unknown", localConfig.maxTokens)
        assertArrayEquals(intArrayOf(1, 10, 11, 0, 2, 0, 0), tokenIds)
    }

    @Test
    fun `detokenizeResponse uses eos and pad correctly`() {
        val localConfig = currentTestModelConfig.copy(eosTokenId = 2, padTokenId = 0)
        val serviceForDetokenTest = LiteRtLlmService(context, localConfig, mockModelDownloader)

        val vocab = mapOf("hello" to 10, "world" to 11, "<eos>" to 2, "<pad>" to 0)
        val vocabMapField = serviceForDetokenTest.javaClass.getDeclaredField("vocabularyMap")
        vocabMapField.isAccessible = true
        (vocabMapField.get(serviceForDetokenTest) as MutableMap<String, Int>).putAll(vocab)

        val ids = intArrayOf(10, 11, 2, 0, 0) // hello world <eos> <pad> <pad>
        val text = serviceForDetokenTest.detokenizeResponsePublic(ids)
        assertEquals("hello world", text.trim())
    }

    @Test
    fun `generateResponse successfully produces text`() = runTest(testDispatcher) {
        // Arrange
        val prompt = "Hi"
        val expectedResponse = "mock response"
        val vocab = mutableMapOf("hi" to 3, "<bos>" to 1, "<eos>" to 2, "<pad>" to 0)
        val responseTokens = mutableListOf<Int>()

        // Create expected response tokens based on the refined tokenizeText
        expectedResponse.split(" ").forEachIndexed { index, token ->
            val tokenId = 10 + index // Assign arbitrary unique IDs
            vocab[token] = tokenId
            responseTokens.add(tokenId)
        }
        // Add EOS to the model's output sequence
        currentTestModelConfig.eosTokenId?.let { responseTokens.add(it) }


        setupVocabulary(vocab) // Setup vocab for the service
        service.initialize()
        advanceUntilIdle()
        assertEquals(LlmServiceState.Ready, service.serviceState.value)

        // Configure the mock interpreter to return our responseTokens
        every { mockInterpreter.run(any<IntBuffer>(), any<IntBuffer>()) } answers {
            val outputBuffer = arg<IntBuffer>(1)
            outputBuffer.clear() // Clear before putting new data
            // Fill with response tokens, then pad if necessary
            for(i in 0 until currentTestModelConfig.maxTokens) {
                outputBuffer.put(responseTokens.getOrElse(i) { currentTestModelConfig.padTokenId })
            }
            outputBuffer.rewind()
        }

        // Act
        val responseFlow = service.generateResponse(prompt, "testConvo", "en")
        val result = responseFlow.first() // Assuming non-streaming for now
        advanceUntilIdle()

        // Assert
        assertEquals(expectedResponse, result.trim())
        verify { mockInterpreter.run(any<IntBuffer>(), any<IntBuffer>()) }
    }

    @Test
    fun `getInitialGreeting successfully produces text`() = runTest(testDispatcher) {
        // Arrange
        val topic = "testing"
        val expectedGreeting = "hello testing"
        val vocab = mutableMapOf("<bos>" to 1, "<eos>" to 2, "<pad>" to 0) // Base vocab
        val greetingTokens = mutableListOf<Int>()

        expectedGreeting.split(" ").forEachIndexed { index, token ->
            val tokenId = 20 + index // Arbitrary unique IDs for "hello testing"
            vocab[token] = tokenId
            greetingTokens.add(tokenId)
        }
        currentTestModelConfig.eosTokenId?.let { greetingTokens.add(it) }


        setupVocabulary(vocab)
        service.initialize()
        advanceUntilIdle()
        assertEquals(LlmServiceState.Ready, service.serviceState.value)

        every { mockInterpreter.run(any<IntBuffer>(), any<IntBuffer>()) } answers {
            val outputBuffer = arg<IntBuffer>(1)
            outputBuffer.clear()
            for(i in 0 until currentTestModelConfig.maxTokens) {
                 outputBuffer.put(greetingTokens.getOrElse(i) { currentTestModelConfig.padTokenId })
            }
            outputBuffer.rewind()
        }

        // Act
        val result = service.getInitialGreeting(topic, "en")
        advanceUntilIdle()

        // Assert
        assertEquals(expectedGreeting, result.trim())
        // Verify run was called. The prompt for initial greeting is internal to the service.
        verify { mockInterpreter.run(any<IntBuffer>(), any<IntBuffer>()) }
    }
}

// Helper extension functions to make tokenizeText and detokenizeResponse testable
// without changing their visibility in the source class.
// This is a workaround for testing private/internal methods.
internal fun LiteRtLlmService.tokenizeTextPublic(inputText: String, maxTokens: Int): IntArray {
    val method = this.javaClass.getDeclaredMethod("tokenizeText", String::class.java, Int::class.java)
    method.isAccessible = true
    return method.invoke(this, inputText, maxTokens) as IntArray
}

internal fun LiteRtLlmService.detokenizeResponsePublic(outputIds: IntArray): String {
    val method = this.javaClass.getDeclaredMethod("detokenizeResponse", IntArray::class.java)
    method.isAccessible = true
    return method.invoke(this, outputIds) as String
}
