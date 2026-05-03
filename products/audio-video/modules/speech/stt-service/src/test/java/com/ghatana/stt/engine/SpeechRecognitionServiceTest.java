package com.ghatana.stt.engine;

import com.ghatana.stt.engine.WhisperTranscriptionEngine.*;
import org.junit.jupiter.api.*;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Test suite for {@link WhisperTranscriptionEngine}.
 *
 * <p>Validates input validation and engine property behaviours that can be exercised
 * independently of the full transcription implementation.
 *
 * @doc.type    class
 * @doc.purpose Input validation and engine property tests for WhisperTranscriptionEngine
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("SpeechRecognitionServiceTest")
class SpeechRecognitionServiceTest {

    private static final byte[] SAMPLE_AUDIO_EN =
            "SAMPLE_PCM_DATA_HELLO_WORLD".getBytes(StandardCharsets.UTF_8);

    private WhisperTranscriptionEngine engine;
    private WhisperTranscriptionEngine diarizationEngine;

    @BeforeEach
    void setUp() {
        engine = new WhisperTranscriptionEngine("whisper-base", false);
        diarizationEngine = new WhisperTranscriptionEngine("whisper-large-v3", true);
    }

    // ===========================================================================
    // INPUT VALIDATION TESTS
    // ===========================================================================

    @Nested
    @DisplayName("input validation")
    class InputValidation {

        @Test
        @DisplayName("null audio data throws TranscriptionException")
        void nullAudioData_throwsTranscriptionException() {
            assertThatThrownBy(() -> engine.transcribe(null, AudioFormat.PCM, "en"))
                    .isInstanceOf(TranscriptionException.class)
                    .hasMessageContaining("must not be null or empty");
        }

        @Test
        @DisplayName("empty audio bytes throws TranscriptionException")
        void emptyAudioBytes_throwsTranscriptionException() {
            byte[] empty = new byte[0];
            assertThatThrownBy(() -> engine.transcribe(empty, AudioFormat.PCM, "en"))
                    .isInstanceOf(TranscriptionException.class)
                    .hasMessageContaining("must not be null or empty");
        }

        @Test
        @DisplayName("null audio format throws NullPointerException")
        void nullAudioFormat_throwsNullPointerException() {
            assertThatThrownBy(() -> engine.transcribe(SAMPLE_AUDIO_EN, null, "en"))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ===========================================================================
    // ENGINE PROPERTIES TESTS
    // ===========================================================================

    @Nested
    @DisplayName("engine properties")
    class EngineProperties {

        @Test
        @DisplayName("engine returns configured model ID")
        void engineReturnsConfiguredModelId() {
            assertThat(engine.getModelId()).isEqualTo("whisper-base");
            assertThat(diarizationEngine.getModelId()).isEqualTo("whisper-large-v3");
        }

        @Test
        @DisplayName("diarization flag is accurately reflected")
        void diarizationFlagIsAccurate() {
            assertThat(engine.isDiarizationEnabled()).isFalse();
            assertThat(diarizationEngine.isDiarizationEnabled()).isTrue();
        }
    }
}
