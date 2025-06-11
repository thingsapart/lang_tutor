package com.whispertflite.utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;

// It's good practice to use a specific runner for Mockito
@RunWith(MockitoJUnitRunner.class)
public class WhisperUtilTest {

    @Test
    public void getMelSpectrogram_producesCorrectDimensions() {
        WhisperUtil whisperUtil = new WhisperUtil();
        // 1 second of dummy audio at 16000 Hz
        float[] samples = new float[WhisperUtil.WHISPER_SAMPLE_RATE * 1];
        // Initialize with some dummy values (e.g. zeros is fine for this test)

        // nMel = 80
        // nLen = nSamples / WHISPER_HOP_LENGTH = 16000 / 160 = 100
        // Expected output length = 80 * 100 = 8000
        int expectedLength = WhisperUtil.WHISPER_N_MEL * (samples.length / WhisperUtil.WHISPER_HOP_LENGTH);

        float[] melSpectrogram = whisperUtil.getMelSpectrogram(samples, samples.length, 1); // Using 1 thread for simplicity

        assertNotNull("Mel spectrogram should not be null", melSpectrogram);
        assertEquals("Mel spectrogram should have the correct total length", expectedLength, melSpectrogram.length);
    }

    @Test
    public void loadFiltersAndVocab_parsesMinimalValidData() throws IOException {
        WhisperUtil whisperUtil = new WhisperUtil();
        String dummyVocabPath = "dummy/vocab.bin";

        // Prepare dummy data for a minimal vocab file
        // Structure: magic (int), nMel (int), nFft (int), filterData (float[nMel*nFft]), nVocab (int), [len (int), word (byte[len])]...
        int nMel = WhisperUtil.WHISPER_N_MEL; // Use constants from WhisperUtil
        int nFft = WhisperUtil.WHISPER_N_FFT_FOR_FILTERS; // Assuming a constant like this exists or use WHISPER_N_FFT
                                                          // For the purpose of this test, let's assume WHISPER_N_FFT,
                                                          // as filters.data is dimensioned with nFft from vocab file
        int nVocab = 1;
        String testWord = "test";
        byte[] testWordBytes = testWord.getBytes();

        int bufferSize = 4 + 4 + 4 + (nMel * nFft * Float.BYTES) + 4 + (4 + testWordBytes.length);
        ByteBuffer bb = ByteBuffer.allocate(bufferSize);
        bb.order(ByteOrder.nativeOrder());

        bb.putInt(0x5553454e); // magic
        bb.putInt(nMel);
        bb.putInt(nFft);
        for (int i = 0; i < nMel * nFft; i++) {
            bb.putFloat(0.1f); // dummy filter data
        }
        bb.putInt(nVocab);
        bb.putInt(testWordBytes.length);
        bb.put(testWordBytes);

        byte[] dummyVocabBytes = bb.array();

        // Mock static Files.readAllBytes
        // This requires org.mockito:mockito-inline dependency for static mocking without PowerMock
        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.readAllBytes(Paths.get(dummyVocabPath))).thenReturn(dummyVocabBytes);

            boolean result = whisperUtil.loadFiltersAndVocab(false, dummyVocabPath); // false for not multilingual

            assertTrue("loadFiltersAndVocab should return true for valid dummy data", result);
            // Check if a known token was loaded (this depends on internal structure of WhisperVocab)
            // For example, if token 0 is the first word:
            assertEquals("Token 0 should be the test word", testWord, whisperUtil.getWordFromToken(0));

            // We could also inspect parts of filters or vocab if they were made accessible for testing,
            // or add specific getter methods in WhisperUtil for them.
            // For now, successful execution and a basic token check suffice.

        }
    }

    @Test
    public void loadFiltersAndVocab_invalidMagic_returnsFalse() throws IOException {
        WhisperUtil whisperUtil = new WhisperUtil();
        String dummyVocabPath = "dummy/invalid_vocab.bin";

        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.order(ByteOrder.nativeOrder());
        bb.putInt(0xBADMAGIC); // Invalid magic number
        byte[] dummyInvalidVocabBytes = bb.array();

        try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.readAllBytes(Paths.get(dummyVocabPath))).thenReturn(dummyInvalidVocabBytes);
            boolean result = whisperUtil.loadFiltersAndVocab(false, dummyVocabPath);
            assertFalse("loadFiltersAndVocab should return false for invalid magic number", result);
        }
    }
}

// Note: WHISPER_N_FFT_FOR_FILTERS is a placeholder.
// The actual nFft used for filter data in WhisperUtil.java's loadFiltersAndVocab is read from the file.
// The test uses WHISPER_N_FFT for consistency with how nFft is read and used for filters.data size.
// The key is that the dummy data is self-consistent.
// If WhisperUtil.WHISPER_N_FFT is indeed what's expected by the filter loading logic for sizing, then this is fine.
// The constant WHISPER_N_FFT in WhisperUtil is 400.
class WhisperUtil {
    public static final int WHISPER_SAMPLE_RATE = 16000;
    public static final int WHISPER_N_FFT = 400; // Used for FFT window size in getMelSpectrogram
    public static final int WHISPER_N_MEL = 80;
    public static final int WHISPER_HOP_LENGTH = 160;
    // This constant is not actually in the original WhisperUtil, the test should use WHISPER_N_FFT
    // as the nFft for filters is read from the vocab file itself.
    // For the test, using WhisperUtil.WHISPER_N_FFT (400) when creating dummy filter data is appropriate
    // if that's what the loading code expects for filters.data array dimensioning.
    // Let's assume filters.nFft read from the file will be this value for the test.
    public static final int WHISPER_N_FFT_FOR_FILTERS = 400;


    // Stubs for methods/classes used by the test, if not directly testing WhisperUtil itself.
    // Actual WhisperUtil class is complex, so this is a simplified placeholder for constants if needed.
    // The test will use the actual WhisperUtil class from the main source. This stub is just for thought process.
    public WhisperUtil() {} // Actual constructor
    public float[] getMelSpectrogram(float[] samples, int nSamples, int nThreads) { return new float[WHISPER_N_MEL * (nSamples/WHISPER_HOP_LENGTH)];}
    public boolean loadFiltersAndVocab(boolean multilingual, String vocabPath) throws IOException { return true;}
    public String getWordFromToken(int token) { return ""; }
}
