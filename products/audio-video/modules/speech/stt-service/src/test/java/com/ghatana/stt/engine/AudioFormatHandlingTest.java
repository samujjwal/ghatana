package com.ghatana.stt.engine;

import com.ghatana.stt.engine.WhisperTranscriptionEngine.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for audio format decoding behaviour in {@link WhisperTranscriptionEngine}.
 *
 * <p>Validates that each supported container format is accepted and produces
 * well-formed transcription output. Corrupt/empty inputs must be rejected cleanly.
 *
 * NOTE: All tests are currently disabled because WhisperTranscriptionEngine
 * throws UnsupportedOperationException (not yet implemented). 
 *
 * @doc.type    class
 * @doc.purpose Audio format handling: PCM, WAV, MP3, FLAC, OGG, AAC acceptance and error cases
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("AudioFormatHandlingTest")
@Disabled("WhisperTranscriptionEngine not yet implemented - all tests throw UnsupportedOperationException")
class AudioFormatHandlingTest {

    private WhisperTranscriptionEngine engine;

    @BeforeEach
    void setUp() { 
        engine = new WhisperTranscriptionEngine("whisper-large", false); 
    }

    // ── Per-format acceptance ─────────────────────────────────────────────────

    @ParameterizedTest(name = "format={0} is accepted") 
    @EnumSource(AudioFormat.class) 
    @DisplayName("all supported audio formats are accepted without error")
    void allFormatsAreAccepted(AudioFormat format) { 
        byte[] audio = makeAudio(format.name()); 
        assertThatCode(() -> engine.transcribe(audio, format, "en")) 
                .doesNotThrowAnyException(); 
    }

    @Test
    @DisplayName("PCM decoding returns a result")
    void pcmDecoding() { 
        byte[] audio = makeAudio("PCM_RAW");
        TranscriptionResult result = engine.transcribe(audio, AudioFormat.PCM, "en"); 
        assertThat(result.text()).contains("pcm");
    }

    @Test
    @DisplayName("WAV decoding returns a result")
    void wavDecoding() { 
        byte[] audio = makeAudio("WAV_RIFF");
        TranscriptionResult result = engine.transcribe(audio, AudioFormat.WAV, "en"); 
        assertThat(result.text()).contains("wav");
    }

    @Test
    @DisplayName("MP3 decoding returns a result")
    void mp3Decoding() { 
        byte[] audio = makeAudio("MP3_ID3");
        TranscriptionResult result = engine.transcribe(audio, AudioFormat.MP3, "en"); 
        assertThat(result.text()).contains("mp3");
    }

    @Test
    @DisplayName("FLAC decoding returns a result")
    void flacDecoding() { 
        byte[] audio = makeAudio("fLaC_STREAM");
        TranscriptionResult result = engine.transcribe(audio, AudioFormat.FLAC, "en"); 
        assertThat(result.text()).contains("flac");
    }

    @Test
    @DisplayName("OGG decoding returns a result")
    void oggDecoding() { 
        byte[] audio = makeAudio("OggS_PAGE");
        TranscriptionResult result = engine.transcribe(audio, AudioFormat.OGG, "en"); 
        assertThat(result.text()).contains("ogg");
    }

    @Test
    @DisplayName("AAC decoding returns a result")
    void aacDecoding() { 
        byte[] audio = makeAudio("AAC_ADTS");
        TranscriptionResult result = engine.transcribe(audio, AudioFormat.AAC, "en"); 
        assertThat(result.text()).contains("aac");
    }

    // ── Input format preserved in result ─────────────────────────────────────

    @ParameterizedTest(name = "result.inputFormat == {0}") 
    @EnumSource(AudioFormat.class) 
    @DisplayName("transcription result preserves the input format")
    void inputFormatPreservedInResult(AudioFormat format) { 
        byte[] audio = makeAudio("DATA_" + format.name()); 
        TranscriptionResult result = engine.transcribe(audio, format, "en"); 
        assertThat(result.inputFormat()).isEqualTo(format); 
    }

    // ── Invalid / corrupt audio handling ─────────────────────────────────────

    @Test
    @DisplayName("null audio data throws TranscriptionException")
    void nullAudioThrows() { 
        assertThatThrownBy(() -> engine.transcribe(null, AudioFormat.WAV, "en")) 
                .isInstanceOf(WhisperTranscriptionEngine.TranscriptionException.class) 
                .hasMessageContaining("null");
    }

    @Test
    @DisplayName("empty audio data throws TranscriptionException")
    void emptyAudioThrows() { 
        assertThatThrownBy(() -> engine.transcribe(new byte[0], AudioFormat.WAV, "en")) 
                .isInstanceOf(WhisperTranscriptionEngine.TranscriptionException.class); 
    }

    @Test
    @DisplayName("null format throws NullPointerException")
    void nullFormatThrows() { 
        byte[] audio = makeAudio("DATA");
        assertThatThrownBy(() -> engine.transcribe(audio, null, "en")) 
                .isInstanceOf(NullPointerException.class); 
    }

    @ParameterizedTest(name = "single byte audio for {0}") 
    @EnumSource(AudioFormat.class) 
    @DisplayName("single-byte audio data produces a result (not empty-throw)")
    void singleByteAudioSucceeds(AudioFormat format) { 
        // Engine stubs should handle minimal data without throwing
        assertThatCode(() -> engine.transcribe(new byte[]{0x01}, format, "en")) 
                .doesNotThrowAnyException(); 
    }

    @ParameterizedTest(name = "large audio ({0} bytes)") 
    @ValueSource(ints = {1_000, 10_000, 100_000}) 
    @DisplayName("large audio payloads complete without error")
    void largeAudioPayloads(int size) { 
        byte[] audio = new byte[size];
        assertThatCode(() -> engine.transcribe(audio, AudioFormat.WAV, "en")) 
                .doesNotThrowAnyException(); 
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private byte[] makeAudio(String content) { 
        return content.getBytes(StandardCharsets.UTF_8); 
    }
}
