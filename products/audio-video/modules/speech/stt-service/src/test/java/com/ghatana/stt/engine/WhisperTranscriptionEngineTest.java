package com.ghatana.stt.engine;

import com.ghatana.stt.engine.WhisperTranscriptionEngine.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link WhisperTranscriptionEngine}.
 *
 * @doc.type    class
 * @doc.purpose WhisperTranscriptionEngine deprecated behavior and fallback guidance
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("WhisperTranscriptionEngineTest [GH-90000]")
@SuppressWarnings({"removal", "deprecation"}) // GH-90000
class WhisperTranscriptionEngineTest {

    private static final byte[] SAMPLE_AUDIO = "SAMPLE_PCM_DATA_HELLO_WORLD".getBytes(StandardCharsets.UTF_8); // GH-90000
    private WhisperTranscriptionEngine engine;

    @BeforeEach
    void setUp() { // GH-90000
        engine = new WhisperTranscriptionEngine("whisper-base", false); // GH-90000
    }

    @Test
    @DisplayName("transcribe throws UnsupportedOperationException with LLM_FALLBACK guidance [GH-90000]")
    void transcribeThrowsWithFallbackGuidance() { // GH-90000
        assertThatThrownBy(() -> engine.transcribe(SAMPLE_AUDIO, AudioFormat.PCM, "en")) // GH-90000
            .isInstanceOf(UnsupportedOperationException.class) // GH-90000
            .hasMessageContaining("LLM_FALLBACK [GH-90000]")
            .hasMessageContaining("GrpcSttClientAdapter [GH-90000]");
    }

    @Test
    @DisplayName("detectLanguage(byte[], format) returns a non-blank language tag [GH-90000]")
    void detectLanguageMethod() { // GH-90000
        String lang = engine.detectLanguage(SAMPLE_AUDIO, AudioFormat.PCM); // GH-90000
        assertThat(lang).isNotBlank(); // GH-90000
    }

    @Test
    @DisplayName("detectLanguage rejects null audio [GH-90000]")
    void nullAudioDataThrows() { // GH-90000
        assertThatThrownBy(() -> engine.detectLanguage(null, AudioFormat.WAV)) // GH-90000
            .isInstanceOf(WhisperTranscriptionEngine.TranscriptionException.class); // GH-90000
    }

    @Test
    @DisplayName("detectLanguage rejects empty audio [GH-90000]")
    void emptyAudioDataThrows() { // GH-90000
        assertThatThrownBy(() -> engine.detectLanguage(new byte[0], AudioFormat.WAV)) // GH-90000
            .isInstanceOf(WhisperTranscriptionEngine.TranscriptionException.class); // GH-90000
    }


    @Test
    @DisplayName("engine reports correct model ID [GH-90000]")
    void engineReportsModelId() { // GH-90000
        assertThat(engine.getModelId()).isEqualTo("whisper-base [GH-90000]");
    }

    @Test
    @DisplayName("engine reports diarization disabled [GH-90000]")
    void engineReportsDiarizationDisabled() { // GH-90000
        assertThat(engine.isDiarizationEnabled()).isFalse(); // GH-90000
    }
}
