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
@DisplayName("WhisperTranscriptionEngineTest")
@SuppressWarnings({"removal", "deprecation"}) 
class WhisperTranscriptionEngineTest {

    private static final byte[] SAMPLE_AUDIO = "SAMPLE_PCM_DATA_HELLO_WORLD".getBytes(StandardCharsets.UTF_8); 
    private WhisperTranscriptionEngine engine;

    @BeforeEach
    void setUp() { 
        engine = new WhisperTranscriptionEngine("whisper-base", false); 
    }

    @Test
    @DisplayName("transcribe throws UnsupportedOperationException with LLM_FALLBACK guidance")
    void transcribeThrowsWithFallbackGuidance() { 
        assertThatThrownBy(() -> engine.transcribe(SAMPLE_AUDIO, AudioFormat.PCM, "en")) 
            .isInstanceOf(UnsupportedOperationException.class) 
            .hasMessageContaining("LLM_FALLBACK")
            .hasMessageContaining("GrpcSttClientAdapter");
    }

    @Test
    @DisplayName("detectLanguage(byte[], format) throws UnsupportedOperationException with LLM_FALLBACK guidance")
    void detectLanguageMethod() { 
        assertThatThrownBy(() -> engine.detectLanguage(SAMPLE_AUDIO, AudioFormat.PCM))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("LLM_FALLBACK")
                .hasMessageContaining("GrpcSttClientAdapter");
    }

    @Test
    @DisplayName("detectLanguage rejects null audio")
    void nullAudioDataThrows() { 
        assertThatThrownBy(() -> engine.detectLanguage(null, AudioFormat.WAV)) 
            .isInstanceOf(WhisperTranscriptionEngine.TranscriptionException.class); 
    }

    @Test
    @DisplayName("detectLanguage rejects empty audio")
    void emptyAudioDataThrows() { 
        assertThatThrownBy(() -> engine.detectLanguage(new byte[0], AudioFormat.WAV)) 
            .isInstanceOf(WhisperTranscriptionEngine.TranscriptionException.class); 
    }


    @Test
    @DisplayName("engine reports correct model ID")
    void engineReportsModelId() { 
        assertThat(engine.getModelId()).isEqualTo("whisper-base");
    }

    @Test
    @DisplayName("engine reports diarization disabled")
    void engineReportsDiarizationDisabled() { 
        assertThat(engine.isDiarizationEnabled()).isFalse(); 
    }
}
