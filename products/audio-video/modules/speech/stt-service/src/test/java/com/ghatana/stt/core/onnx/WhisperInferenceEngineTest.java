package com.ghatana.stt.core.onnx;

import ai.onnxruntime.OrtException;
import com.ghatana.stt.core.api.TranscriptionOptions;
import com.ghatana.stt.core.api.TranscriptionResult;
import com.ghatana.stt.core.config.ModelConfig;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WhisperInferenceEngine.
 */
@Tag("integration")
@DisplayName("WhisperInferenceEngine")
class WhisperInferenceEngineTest {

    @TempDir
    Path tempDir;

    private ModelConfig modelConfig;
    private TranscriptionOptions defaultOptions;

    @BeforeEach
    void setUp() {
        modelConfig = ModelConfig.defaults();
        defaultOptions = TranscriptionOptions.defaults();
    }

    @Nested
    @DisplayName("Initialization")
    class Initialization {

        @Test
        @DisplayName("should create engine with valid configuration")
        void shouldCreateEngineWithValidConfiguration() throws OrtException {
            // GIVEN valid model path and config
            Path modelPath = tempDir.resolve("models");

            // WHEN creating engine
            WhisperInferenceEngine engine = new WhisperInferenceEngine(modelConfig);

            // THEN engine should be created
            assertNotNull(engine);
        }

        @Test
        @DisplayName("should throw on null model path")
        void shouldThrowOnNullModelPath() {
            // GIVEN null model path
            // WHEN/THEN creating engine should throw
            assertThrows(NullPointerException.class, () -> new WhisperInferenceEngine(null));
        }
    }

    @Nested
    @DisplayName("Audio Processing")
    class AudioProcessing {

        @Test
        @DisplayName("should handle empty audio gracefully")
        void shouldHandleEmptyAudioGracefully() throws OrtException {
            // GIVEN engine and empty audio
            WhisperInferenceEngine engine = new WhisperInferenceEngine(modelConfig);
            float[] emptyAudio = new float[0];

            // WHEN transcribing empty audio
            TranscriptionResult result = engine.transcribe(emptyAudio, defaultOptions);

            // THEN should return empty result
            assertNotNull(result);
            assertTrue(result.text().isEmpty() || result.text().isBlank());
        }

        @Test
        @DisplayName("should handle short audio segments")
        void shouldHandleShortAudioSegments() throws OrtException {
            // GIVEN engine and very short audio (100ms)
            WhisperInferenceEngine engine = new WhisperInferenceEngine(modelConfig);
            float[] shortAudio = new float[1600]; // 100ms at 16kHz

            // WHEN transcribing short audio
            TranscriptionResult result = engine.transcribe(shortAudio, defaultOptions);

            // THEN should return result without error
            assertNotNull(result);
        }

        @Test
        @DisplayName("should process standard length audio")
        void shouldProcessStandardLengthAudio() throws OrtException {
            // GIVEN engine and 5 second audio
            WhisperInferenceEngine engine = new WhisperInferenceEngine(modelConfig);
            float[] audio = generateSineWave(5.0f, 440.0f, 16000);

            // WHEN transcribing
            TranscriptionResult result = engine.transcribe(audio, defaultOptions);

            // THEN should return valid result
            assertNotNull(result);
            assertTrue(result.processingTimeMs() >= 0);
        }

        @Test
        @DisplayName("should handle long audio with chunking")
        void shouldHandleLongAudioWithChunking() throws OrtException {
            // GIVEN engine and 60 second audio (requires chunking)
            WhisperInferenceEngine engine = new WhisperInferenceEngine(modelConfig);
            float[] longAudio = generateSineWave(60.0f, 440.0f, 16000);

            // WHEN transcribing long audio
            TranscriptionResult result = engine.transcribeLongAudio(longAudio, defaultOptions);

            // THEN should return result
            assertNotNull(result);
            assertTrue(result.isFinal());
        }
    }

    @Nested
    @DisplayName("Transcription Options")
    class TranscriptionOptionsTests {

        @Test
        @DisplayName("should apply transcription options")
        void shouldApplyTranscriptionOptions() throws OrtException {
            // GIVEN engine and options with specific language
            WhisperInferenceEngine engine = new WhisperInferenceEngine(modelConfig);
            TranscriptionOptions options = TranscriptionOptions.builder()
                    .language("en")
                    .build();
            float[] audio = generateSineWave(1.0f, 440.0f, 16000);

            // WHEN transcribing
            TranscriptionResult result = engine.transcribe(audio, options);

            // THEN should use specified language
            assertNotNull(result);
            assertEquals("en", result.language());
        }

        @Test
        @DisplayName("should include word timings when enabled")
        void shouldIncludeWordTimingsWhenEnabled() throws OrtException {
            // GIVEN engine and options with word timings enabled
            WhisperInferenceEngine engine = new WhisperInferenceEngine(modelConfig);
            TranscriptionOptions options = TranscriptionOptions.builder()
                    .enableWordTimings(true)
                    .build();
            float[] audio = generateSineWave(2.0f, 440.0f, 16000);

            // WHEN transcribing
            TranscriptionResult result = engine.transcribe(audio, options);

            // THEN should include word timings
            assertNotNull(result);
            assertNotNull(result.wordTimings());
        }
    }

    @Nested
    @DisplayName("Model Management")
    class ModelManagement {

        @Test
        @DisplayName("should report loaded state correctly")
        void shouldReportLoadedStateCorrectly() throws OrtException {
            // GIVEN engine
            WhisperInferenceEngine engine = new WhisperInferenceEngine(modelConfig);

            // WHEN checking if loaded (before loading)
            boolean loaded = engine.isModelLoaded();

            // THEN should report not loaded
            assertFalse(loaded);
        }

        @Test
        @DisplayName("should close resources properly")
        void shouldCloseResourcesProperly() throws OrtException {
            // GIVEN engine
            WhisperInferenceEngine engine = new WhisperInferenceEngine(modelConfig);

            // WHEN closing
            assertDoesNotThrow(engine::close);

            // THEN should be closed
            assertFalse(engine.isModelLoaded());
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private float[] generateSineWave(float durationSeconds, float frequency, int sampleRate) {
        int numSamples = (int) (durationSeconds * sampleRate);
        float[] samples = new float[numSamples];

        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / sampleRate;
            samples[i] = (float) (0.5 * Math.sin(2 * Math.PI * frequency * t));
        }

        return samples;
    }
}
