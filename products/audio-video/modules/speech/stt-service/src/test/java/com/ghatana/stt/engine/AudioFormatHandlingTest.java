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
 * throws UnsupportedOperationException (not yet implemented). // GH-90000
 *
 * @doc.type    class
 * @doc.purpose Audio format handling: PCM, WAV, MP3, FLAC, OGG, AAC acceptance and error cases
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("AudioFormatHandlingTest [GH-90000]")
@Disabled("WhisperTranscriptionEngine not yet implemented - all tests throw UnsupportedOperationException [GH-90000]")
class AudioFormatHandlingTest {

    private WhisperTranscriptionEngine engine;

    @BeforeEach
    void setUp() { // GH-90000
        engine = new WhisperTranscriptionEngine("whisper-large", false); // GH-90000
    }

    // ── Per-format acceptance ─────────────────────────────────────────────────

    @ParameterizedTest(name = "format={0} is accepted") // GH-90000
    @EnumSource(AudioFormat.class) // GH-90000
    @DisplayName("all supported audio formats are accepted without error [GH-90000]")
    void allFormatsAreAccepted(AudioFormat format) { // GH-90000
        byte[] audio = makeAudio(format.name()); // GH-90000
        assertThatCode(() -> engine.transcribe(audio, format, "en")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @Test
    @DisplayName("PCM decoding returns a result [GH-90000]")
    void pcmDecoding() { // GH-90000
        byte[] audio = makeAudio("PCM_RAW [GH-90000]");
        TranscriptionResult result = engine.transcribe(audio, AudioFormat.PCM, "en"); // GH-90000
        assertThat(result.text()).contains("pcm [GH-90000]");
    }

    @Test
    @DisplayName("WAV decoding returns a result [GH-90000]")
    void wavDecoding() { // GH-90000
        byte[] audio = makeAudio("WAV_RIFF [GH-90000]");
        TranscriptionResult result = engine.transcribe(audio, AudioFormat.WAV, "en"); // GH-90000
        assertThat(result.text()).contains("wav [GH-90000]");
    }

    @Test
    @DisplayName("MP3 decoding returns a result [GH-90000]")
    void mp3Decoding() { // GH-90000
        byte[] audio = makeAudio("MP3_ID3 [GH-90000]");
        TranscriptionResult result = engine.transcribe(audio, AudioFormat.MP3, "en"); // GH-90000
        assertThat(result.text()).contains("mp3 [GH-90000]");
    }

    @Test
    @DisplayName("FLAC decoding returns a result [GH-90000]")
    void flacDecoding() { // GH-90000
        byte[] audio = makeAudio("fLaC_STREAM [GH-90000]");
        TranscriptionResult result = engine.transcribe(audio, AudioFormat.FLAC, "en"); // GH-90000
        assertThat(result.text()).contains("flac [GH-90000]");
    }

    @Test
    @DisplayName("OGG decoding returns a result [GH-90000]")
    void oggDecoding() { // GH-90000
        byte[] audio = makeAudio("OggS_PAGE [GH-90000]");
        TranscriptionResult result = engine.transcribe(audio, AudioFormat.OGG, "en"); // GH-90000
        assertThat(result.text()).contains("ogg [GH-90000]");
    }

    @Test
    @DisplayName("AAC decoding returns a result [GH-90000]")
    void aacDecoding() { // GH-90000
        byte[] audio = makeAudio("AAC_ADTS [GH-90000]");
        TranscriptionResult result = engine.transcribe(audio, AudioFormat.AAC, "en"); // GH-90000
        assertThat(result.text()).contains("aac [GH-90000]");
    }

    // ── Input format preserved in result ─────────────────────────────────────

    @ParameterizedTest(name = "result.inputFormat == {0}") // GH-90000
    @EnumSource(AudioFormat.class) // GH-90000
    @DisplayName("transcription result preserves the input format [GH-90000]")
    void inputFormatPreservedInResult(AudioFormat format) { // GH-90000
        byte[] audio = makeAudio("DATA_" + format.name()); // GH-90000
        TranscriptionResult result = engine.transcribe(audio, format, "en"); // GH-90000
        assertThat(result.inputFormat()).isEqualTo(format); // GH-90000
    }

    // ── Invalid / corrupt audio handling ─────────────────────────────────────

    @Test
    @DisplayName("null audio data throws TranscriptionException [GH-90000]")
    void nullAudioThrows() { // GH-90000
        assertThatThrownBy(() -> engine.transcribe(null, AudioFormat.WAV, "en")) // GH-90000
                .isInstanceOf(WhisperTranscriptionEngine.TranscriptionException.class) // GH-90000
                .hasMessageContaining("null [GH-90000]");
    }

    @Test
    @DisplayName("empty audio data throws TranscriptionException [GH-90000]")
    void emptyAudioThrows() { // GH-90000
        assertThatThrownBy(() -> engine.transcribe(new byte[0], AudioFormat.WAV, "en")) // GH-90000
                .isInstanceOf(WhisperTranscriptionEngine.TranscriptionException.class); // GH-90000
    }

    @Test
    @DisplayName("null format throws NullPointerException [GH-90000]")
    void nullFormatThrows() { // GH-90000
        byte[] audio = makeAudio("DATA [GH-90000]");
        assertThatThrownBy(() -> engine.transcribe(audio, null, "en")) // GH-90000
                .isInstanceOf(NullPointerException.class); // GH-90000
    }

    @ParameterizedTest(name = "single byte audio for {0}") // GH-90000
    @EnumSource(AudioFormat.class) // GH-90000
    @DisplayName("single-byte audio data produces a result (not empty-throw) [GH-90000]")
    void singleByteAudioSucceeds(AudioFormat format) { // GH-90000
        // Engine stubs should handle minimal data without throwing
        assertThatCode(() -> engine.transcribe(new byte[]{0x01}, format, "en")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    @ParameterizedTest(name = "large audio ({0} bytes)") // GH-90000
    @ValueSource(ints = {1_000, 10_000, 100_000}) // GH-90000
    @DisplayName("large audio payloads complete without error [GH-90000]")
    void largeAudioPayloads(int size) { // GH-90000
        byte[] audio = new byte[size];
        assertThatCode(() -> engine.transcribe(audio, AudioFormat.WAV, "en")) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private byte[] makeAudio(String content) { // GH-90000
        return content.getBytes(StandardCharsets.UTF_8); // GH-90000
    }
}
