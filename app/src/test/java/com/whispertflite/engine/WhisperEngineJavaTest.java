package com.whispertflite.engine;

import android.content.Context;
import com.whispertflite.utils.WhisperUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class WhisperEngineJavaTest {

    @Mock
    private Context mockContext; // Mocked because WhisperEngineJava takes Context

    @Mock
    private WhisperUtil mockWhisperUtil; // To control its behavior

    // We will mock the Interpreter construction itself
    @Mock
    private Interpreter mockInterpreter;


    private WhisperEngineJava whisperEngineJava;

    @Before
    public void setUp() {
        // It's important to reinstantiate or reset mocks if WhisperEngineJava has state
        // Here, we create a new instance for each test.
        // The mockWhisperUtil will be injected if WhisperEngineJava is refactored for DI,
        // or we can mock its construction if it's instantiated internally with "new WhisperUtil()".
        // For now, WhisperEngineJava creates its own WhisperUtil. We'll use constructor mocking for it.
        // Same for Interpreter.
    }

    @Test
    public void initialize_successPath_setsInitializedTrue() throws IOException {
        // Arrange
        String modelPath = "dummy_model.tflite";
        String vocabPath = "dummy_vocab.bin";

        // Mock construction of WhisperUtil internally used by WhisperEngineJava
        try (MockedConstruction<WhisperUtil> mockedWhisperUtilConstruction = Mockito.mockConstruction(WhisperUtil.class,
                (mock, context) -> {
                    // 'mock' is the instance of WhisperUtil created by WhisperEngineJava
                    // 'context' is the construction context
                    when(mock.loadFiltersAndVocab(anyBoolean(), eq(vocabPath))).thenReturn(true);
                });
            MockedConstruction<Interpreter.Options> mockedOptionsConstruction = Mockito.mockConstruction(Interpreter.Options.class);
            MockedConstruction<FileInputStream> mockedFISConstruction = Mockito.mockConstruction(FileInputStream.class,
                (mock, context) -> {
                    FileChannel mockFileChannel = mock(FileChannel.class);
                    when(mockFileChannel.map(any(FileChannel.MapMode.class), anyLong(), anyLong())).thenReturn(mock(ByteBuffer.class));
                    when(mock.getChannel()).thenReturn(mockFileChannel);
                });
            // Mock the construction of the Interpreter
            MockedConstruction<Interpreter> mockedInterpreterConstruction = Mockito.mockConstruction(Interpreter.class,
                (mock, context) -> {
                    // 'mock' is the instance of Interpreter created
                    // We can store this instance if we need to verify calls on it later outside initialize
                    // For this test, just ensuring it's created is part of the success path.
                })) {

            whisperEngineJava = new WhisperEngineJava(mockContext); // mWhisperUtil is created inside
            boolean result = whisperEngineJava.initialize(modelPath, vocabPath, false);

            assertTrue("initialize should return true on success", result);
            assertTrue("mIsInitialized should be true after successful initialization", whisperEngineJava.isInitialized());

            // Verify that loadFiltersAndVocab was called on the constructed WhisperUtil instance
            // This requires getting the mocked instance from mockedWhisperUtilConstruction if needed for more specific verify
            // For now, the when().thenReturn(true) covers the interaction.
        }
    }

    @Test(expected = IOException.class)
    public void initialize_modelLoadFails_throwsIOException() throws IOException {
        String modelPath = "invalid_model.tflite";
        String vocabPath = "dummy_vocab.bin";

        try (MockedConstruction<WhisperUtil> mockedWhisperUtilConstruction = Mockito.mockConstruction(WhisperUtil.class);
             MockedConstruction<Interpreter.Options> mockedOptionsConstruction = Mockito.mockConstruction(Interpreter.Options.class);
             MockedConstruction<FileInputStream> mockedFISConstruction = Mockito.mockConstruction(FileInputStream.class,
                (mock, context) -> {
                    when(mock.getChannel()).thenThrow(new IOException("Failed to open model"));
                })) {

            whisperEngineJava = new WhisperEngineJava(mockContext);
            whisperEngineJava.initialize(modelPath, vocabPath, false); // This should throw
        }
    }

    @Test
    public void initialize_vocabLoadFails_setsInitializedFalse() throws IOException {
        String modelPath = "dummy_model.tflite";
        String vocabPath = "invalid_vocab.bin";

        try (MockedConstruction<WhisperUtil> mockedWhisperUtilConstruction = Mockito.mockConstruction(WhisperUtil.class,
                (mock, context) -> {
                    when(mock.loadFiltersAndVocab(anyBoolean(), eq(vocabPath))).thenReturn(false);
                });
            MockedConstruction<Interpreter.Options> mockedOptionsConstruction = Mockito.mockConstruction(Interpreter.Options.class);
            MockedConstruction<FileInputStream> mockedFISConstruction = Mockito.mockConstruction(FileInputStream.class,
                (mock, context) -> {
                    FileChannel mockFileChannel = mock(FileChannel.class);
                    when(mockFileChannel.map(any(FileChannel.MapMode.class), anyLong(), anyLong())).thenReturn(mock(ByteBuffer.class));
                    when(mock.getChannel()).thenReturn(mockFileChannel);
                });
            MockedConstruction<Interpreter> mockedInterpreterConstruction = Mockito.mockConstruction(Interpreter.class)) {

            whisperEngineJava = new WhisperEngineJava(mockContext);
            boolean result = whisperEngineJava.initialize(modelPath, vocabPath, false);

            assertFalse("initialize should return false if vocab loading fails", result);
            assertFalse("mIsInitialized should be false if vocab loading fails", whisperEngineJava.isInitialized());
        }
    }


    @Test
    public void deinitialize_closesInterpreter_ifNotNull() throws IOException {
        // First, initialize to make mInterpreter non-null
        String modelPath = "dummy_model.tflite";
        String vocabPath = "dummy_vocab.bin";
        Interpreter rememberedMockInterpreter = null;

        try (MockedConstruction<WhisperUtil> mockedWU = Mockito.mockConstruction(WhisperUtil.class, (mock, ctx) -> {
                when(mock.loadFiltersAndVocab(anyBoolean(), anyString())).thenReturn(true);
             });
             MockedConstruction<Interpreter.Options> mockedOpts = Mockito.mockConstruction(Interpreter.Options.class);
             MockedConstruction<FileInputStream> mockedFIS = Mockito.mockConstruction(FileInputStream.class, (mock, ctx) -> {
                 FileChannel mockFc = mock(FileChannel.class);
                 when(mockFc.map(any(), anyLong(), anyLong())).thenReturn(mock(ByteBuffer.class));
                 when(mock.getChannel()).thenReturn(mockFc);
             });
             // Use a container to capture the mock interpreter instance
             MockedConstruction<Interpreter> mockedInterp = Mockito.mockConstruction(Interpreter.class, (mock, ctx) -> {
                 // Store this mock instance to verify close() on it later
                 // This is a bit of a hack; real DI would be cleaner
             })) {

            whisperEngineJava = new WhisperEngineJava(mockContext);
            whisperEngineJava.initialize(modelPath, vocabPath, false);

            // Retrieve the created mock interpreter instance (there should be only one)
            // This is tricky as the instance is inside WhisperEngineJava.
            // A better way would be to inject the Interpreter or its factory.
            // For this test, we assume initialize creates one, and deinitialize will use it.
            // If we can't get the instance, we can at least verify that if it *were* our mockInterpreter, close would be called.
            // However, with MockedConstruction, the mock IS the one created. So we need to get it.
            // Let's assume the first one created is the one.
            if (!mockedInterp.constructed().isEmpty()) {
                rememberedMockInterpreter = mockedInterp.constructed().get(0);
            }
        }

        assertNotNull("Interpreter should have been constructed", rememberedMockInterpreter);
        assertTrue("Engine should be initialized", whisperEngineJava.isInitialized());

        // Act
        whisperEngineJava.deinitialize();

        // Assert
        verify(rememberedMockInterpreter).close(); // Verify close() was called on the constructed Interpreter
        assertFalse("mIsInitialized should be false after deinitialization", whisperEngineJava.isInitialized());
        // Also, mInterpreter field in WhisperEngineJava should be null after deinitialize.
        // This would require reflection to check, or a getter, or trusting the implementation.
    }

    @Test
    public void deinitialize_doesNothing_ifInterpreterIsNull() {
        // Ensure mInterpreter is null (e.g., by not calling initialize or if init failed)
        whisperEngineJava = new WhisperEngineJava(mockContext); // mInterpreter is null here

        // We need to ensure no NullPointerException is thrown
        try {
            whisperEngineJava.deinitialize();
        } catch (NullPointerException e) {
            fail("deinitialize should not throw NullPointerException if interpreter is already null");
        }
        assertFalse("mIsInitialized should remain false", whisperEngineJava.isInitialized());
    }

    // --- Tests for transcribeBuffer ---

    @Test
    public void transcribeBuffer_withNullSamples_returnsEmptyString() {
        // No initialization needed for this specific path
        whisperEngineJava = new WhisperEngineJava(mockContext);
        String result = whisperEngineJava.transcribeBuffer(null);
        assertEquals("Result should be empty string for null samples", "", result);
    }

    @Test
    public void transcribeBuffer_withEmptySamples_returnsEmptyString() {
        // No initialization needed for this specific path
        whisperEngineJava = new WhisperEngineJava(mockContext);
        String result = whisperEngineJava.transcribeBuffer(new float[0]);
        assertEquals("Result should be empty string for empty samples", "", result);
    }

    @Test
    public void transcribeBuffer_withValidSamples_processesCorrectly() throws IOException {
        // Arrange: Need to initialize the engine to get mInterpreter and mWhisperUtil mocked via construction.
        String modelPath = "dummy_model.tflite";
        String vocabPath = "dummy_vocab.bin";

        float[] inputSamples = new float[16000]; // 1 second
        java.util.Arrays.fill(inputSamples, 0.1f);

        // These will be the instances created by 'new' inside WhisperEngineJava
        final WhisperUtil[] constructedWhisperUtil = new WhisperUtil[1];
        final Interpreter[] constructedInterpreter = new Interpreter[1];

        try (MockedConstruction<WhisperUtil> mockedWhisperUtilConstruction = Mockito.mockConstruction(WhisperUtil.class,
                (mock, context) -> {
                    constructedWhisperUtil[0] = mock; // Capture the mock instance
                    // Define behavior for getMelSpectrogram
                    float[] dummyMelSpectrogram = new float[com.whispertflite.utils.WhisperUtil.WHISPER_N_MEL * 100]; // Dummy mel data
                    when(mock.getMelSpectrogram(any(float[].class),
                                                eq(com.whispertflite.utils.WhisperUtil.WHISPER_SAMPLE_RATE * com.whispertflite.utils.WhisperUtil.WHISPER_CHUNK_SIZE),
                                                anyInt()))
                        .thenReturn(dummyMelSpectrogram);

                    // Define behavior for token translations
                    when(mock.getTokenEOT()).thenReturn(50256); // Example EOT token
                    when(mock.getWordFromToken(1)).thenReturn("hello");
                    when(mock.getWordFromToken(2)).thenReturn("world");
                    when(mock.loadFiltersAndVocab(anyBoolean(), eq(vocabPath))).thenReturn(true); // For initialization
                });
            MockedConstruction<Interpreter.Options> mockedOptionsConstruction = Mockito.mockConstruction(Interpreter.Options.class);
            MockedConstruction<FileInputStream> mockedFISConstruction = Mockito.mockConstruction(FileInputStream.class,
                (mock, context) -> {
                    FileChannel mockFileChannel = mock(FileChannel.class);
                    when(mockFileChannel.map(any(FileChannel.MapMode.class), anyLong(), anyLong())).thenReturn(mock(ByteBuffer.class));
                    when(mock.getChannel()).thenReturn(mockFileChannel);
                });
            MockedConstruction<Interpreter> mockedInterpreterConstruction = Mockito.mockConstruction(Interpreter.class,
                (mock, context) -> {
                    constructedInterpreter[0] = mock; // Capture the mock instance
                    // Define behavior for mInterpreter.run(...)
                    doAnswer(invocation -> {
                        ByteBuffer outputBB = invocation.getArgument(1); // The output buffer from runInference
                        outputBB.clear();
                        outputBB.putFloat(1.0f); // token 1 -> "hello"
                        outputBB.putFloat(2.0f); // token 2 -> "world"
                        outputBB.putFloat((float) 50256); // EOT token
                        outputBB.rewind();
                        return null;
                    }).when(mock).run(any(ByteBuffer.class), any(ByteBuffer.class));

                    // Mock tensor behavior if needed for initialization or other parts of runInference
                    Tensor mockInputTensor = mock(Tensor.class);
                    when(mockInputTensor.shape()).thenReturn(new int[]{1, 80, 3000}); // Example shape
                    when(mockInputTensor.dataType()).thenReturn(org.tensorflow.lite.DataType.FLOAT32);
                    when(mock.getInputTensor(0)).thenReturn(mockInputTensor);

                    Tensor mockOutputTensor = mock(Tensor.class);
                    when(mockOutputTensor.shape()).thenReturn(new int[]{1, 100}); // Example output shape
                    when(mock.getOutputTensor(0)).thenReturn(mockOutputTensor);

                })) {

            whisperEngineJava = new WhisperEngineJava(mockContext);
            whisperEngineJava.initialize(modelPath, vocabPath, false); // Initialize to set up mocks

            // Act
            String result = whisperEngineJava.transcribeBuffer(inputSamples);

            // Assert
            assertEquals("Transcription result is not as expected", "helloworld", result);

            // Verify getMelSpectrogram was called correctly
            ArgumentCaptor<float[]> samplesCaptor = ArgumentCaptor.forClass(float[].class);
            verify(constructedWhisperUtil[0]).getMelSpectrogram(
                    samplesCaptor.capture(),
                    eq(com.whispertflite.utils.WhisperUtil.WHISPER_SAMPLE_RATE * com.whispertflite.utils.WhisperUtil.WHISPER_CHUNK_SIZE),
                    anyInt());

            // Check that the captured samples have the expected length (fixedInputSize)
            assertEquals(com.whispertflite.utils.WhisperUtil.WHISPER_SAMPLE_RATE * com.whispertflite.utils.WhisperUtil.WHISPER_CHUNK_SIZE,
                         samplesCaptor.getValue().length);
            // Check if the beginning of the captured samples matches inputSamples
            for(int i=0; i < inputSamples.length; i++) {
                assertEquals(inputSamples[i], samplesCaptor.getValue()[i], 0.001f);
            }

            // Verify interpreter run was called (implicitly done by checking the result, but can be explicit)
            verify(constructedInterpreter[0]).run(any(ByteBuffer.class), any(ByteBuffer.class));
        }
    }
}
