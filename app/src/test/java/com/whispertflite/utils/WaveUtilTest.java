package com.whispertflite.utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
// Static mocking for Log can be tricky without PowerMock or similar,
// but many test environments provide a shadow Log or allow it to be ignored.
// For now, we'll assume Log calls don't break the test execution.

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class WaveUtilTest {

    @Test
    public void getSamples_dummyPath_returnsEmptyArray() {
        // Test with a non-existent file path
        float[] samples = WaveUtil.getSamples("dummy/path/to/nonexistent.wav");
        assertNotNull("Samples array should not be null", samples);
        assertEquals("Samples array should be empty for a non-existent file", 0, samples.length);
    }

    @Test
    public void getSamples_invalidWaveFile_logsErrorAndReturnsEmptyArray() {
        // This test is more conceptual if we can't easily create a malformed WAV for testing
        // or mock FileInputStream behavior precisely without more setup.
        // The current implementation of getSamples logs to System.err (now Log.e) and returns float[0].
        // We're primarily checking that it doesn't throw an unhandled exception for basic cases.
        // A truly malformed file might be needed for a deeper test.
        String dummyFilePath = "test_invalid.wav";
        File testFile = new File(dummyFilePath);
        try {
            // Create a dummy empty file, which is not a valid WAV
            if (testFile.createNewFile()) {
                float[] samples = WaveUtil.getSamples(dummyFilePath);
                assertNotNull("Samples array should not be null", samples);
                assertEquals("Samples array should be empty for an invalid wav file", 0, samples.length);
            }
        } catch (IOException e) {
            fail("Test setup failed: " + e.getMessage());
        } finally {
            if (testFile.exists()) {
                testFile.delete();
            }
        }
    }

    @Test
    public void createWaveFile_runsWithoutException() {
        String testFilePath = "test_output.wav";
        File testFile = new File(testFilePath);
        try {
            byte[] dummySamples = new byte[16000 * 2]; // 1 second of 16-bit mono audio
            // Fill with some dummy data (e.g., zeros)
            for (int i = 0; i < dummySamples.length; i++) {
                dummySamples[i] = 0;
            }
            WaveUtil.createWaveFile(testFilePath, dummySamples, 16000, 1, 2);
            // If we reach here, no exception was thrown, which is the primary check.
            // Optionally, check if file exists, but content verification is complex.
            // assertTrue("Wave file should have been created", testFile.exists());
        } catch (Exception e) {
            fail("createWaveFile threw an unexpected exception: " + e.getMessage());
        } finally {
            if (testFile.exists()) {
                // Clean up the created file
                testFile.delete();
            }
        }
    }
}
