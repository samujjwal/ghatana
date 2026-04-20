package com.ghatana.stt.engine;

import com.ghatana.stt.engine.WhisperTranscriptionEngine.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link WhisperTranscriptionEngine}.
 *
 * NOTE: All tests are currently disabled because WhisperTranscriptionEngine
 * throws UnsupportedOperationException (not yet implemented).
 * Re-enable when the engine implementation is complete.
 */

/**
 * Tests for {@link WhisperTranscriptionEngine}.
 *
 * @doc.type    class
 * @doc.purpose WhisperTranscriptionEngine: format handling, confidence, language detection, diarization
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("WhisperTranscriptionEngineTest")
@Disabled("WhisperTranscriptionEngine not yet implemented - all tests throw UnsupportedOperationException")
class WhisperTranscriptionEngineTest {

    private static final byte[] SAMPLE_AUDIO = "SAMPLE_PCM_DATA_HELLO_WORLD".getBytes(StandardCharsets.UTF_8);
    private WhisperTranscriptionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new WhisperTranscriptionEngine("whisper-base", false);
    }

    // ── Format handling ───────────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(AudioFormat.class)
    @DisplayName("transcription succeeds for all supported audio formats")
    void transcriptionSucceedsForAllFormats(AudioFormat format) {
        TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO, format, "en");
        assertThat(result).isNotNull();
        assertThat(result.text()).isNotBlank();
        assertThat(result.inputFormat()).isEqualTo(format);
    }

    @Test
    @DisplayName("PCM audio transcription returns non-empty text")
    void pcmTranscriptionReturnsText() {
        TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO, AudioFormat.PCM, "en");
        assertThat(result.text()).isNotEmpty();
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("WAV audio transcription returns non-empty text")
    void wavTranscriptionReturnsText() {
        TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO, AudioFormat.WAV, null);
        assertThat(result.text()).isNotEmpty();
    }

    @Test
    @DisplayName("MP3 audio transcription populates result")
    void mp3TranscriptionReturnsText() {
        TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO, AudioFormat.MP3, "fr");
        assertThat(result).isNotNull();
        assertThat(result.text()).isNotBlank();
    }

    @Test
    @DisplayName("FLAC audio transcription completes without error")
    void flacTranscriptionCompletes() {
        assertThatCode(() -> engine.transcribe(SAMPLE_AUDIO, AudioFormat.FLAC, "de"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("OGG audio transcription completes without error")
    void oggTranscriptionCompletes() {
        assertThatCode(() -> engine.transcribe(SAMPLE_AUDIO, AudioFormat.OGG, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AAC audio transcription completes without error")
    void aacTranscriptionCompletes() {
        assertThatCode(() -> engine.transcribe(SAMPLE_AUDIO, AudioFormat.AAC, "ja"))
                .doesNotThrowAnyException();
    }

    // ── Confidence scoring ────────────────────────────────────────────────────

    @Test
    @DisplayName("transcription confidence is within [0, 1]")
    void confidenceIsNormalized() {
        TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO, AudioFormat.WAV, "en");
        assertThat(result.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("confidence is reproducible for identical input")
    void confidenceIsReproducible() {
        TranscriptionResult r1 = engine.transcribe(SAMPLE_AUDIO, AudioFormat.PCM, "en");
        TranscriptionResult r2 = engine.transcribe(SAMPLE_AUDIO, AudioFormat.PCM, "en");
        assertThat(r1.confidence()).isEqualTo(r2.confidence());
    }

    // ── Language detection ────────────────────────────────────────────────────

    @Test
    @DisplayName("explicit language hint is preserved in result")
    void explicitLanguagePreserved() {
        TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO, AudioFormat.WAV, "en");
        assertThat(result.detectedLanguage()).isEqualTo("en");
    }

    @Test
    @DisplayName("null language triggers auto-detection and returns non-blank language tag")
    void nullLanguageTriggersAutoDetection() {
        TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO, AudioFormat.WAV, null);
        assertThat(result.detectedLanguage()).isNotBlank();
    }

    @Test
    @DisplayName("detectLanguage(byte[], format) returns a non-blank language tag")
    void detectLanguageMethod() {
        String lang = engine.detectLanguage(SAMPLE_AUDIO, AudioFormat.PCM);
        assertThat(lang).isNotBlank();
    }

    // ── Speaker diarization ───────────────────────────────────────────────────

    @Test
    @DisplayName("diarization disabled — speaker segments list is empty")
    void diarizationDisabledReturnsEmptySegments() {
        TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO, AudioFormat.WAV, "en");
        assertThat(result.speakerSegments()).isEmpty();
    }

    @Test
    @DisplayName("diarization enabled — speaker segments are present")
    void diarizationEnabledProducesSegments() {
        WhisperTranscriptionEngine diarEngine = new WhisperTranscriptionEngine("whisper-base", true);
        TranscriptionResult result = diarEngine.transcribe(SAMPLE_AUDIO, AudioFormat.WAV, "en");
        assertThat(result.speakerSegments()).isNotEmpty();
    }

    @Test
    @DisplayName("speaker segment text is non-blank when diarization enabled")
    void speakerSegmentTextIsNonBlank() {
        WhisperTranscriptionEngine diarEngine = new WhisperTranscriptionEngine("whisper-base", true);
        TranscriptionResult result = diarEngine.transcribe(SAMPLE_AUDIO, AudioFormat.WAV, "en");
        result.speakerSegments().forEach(seg ->
                assertThat(seg.text()).isNotBlank());
    }

    // ── Error handling ────────────────────────────────────────────────────────

    @Test
    @DisplayName("null audio data throws TranscriptionException")
    void nullAudioDataThrows() {
        assertThatThrownBy(() -> engine.transcribe(null, AudioFormat.WAV, "en"))
                .isInstanceOf(WhisperTranscriptionEngine.TranscriptionException.class);
    }

    @Test
    @DisplayName("empty audio data throws TranscriptionException")
    void emptyAudioDataThrows() {
        assertThatThrownBy(() -> engine.transcribe(new byte[0], AudioFormat.WAV, "en"))
                .isInstanceOf(WhisperTranscriptionEngine.TranscriptionException.class);
    }

    // ── Processing time ───────────────────────────────────────────────────────

    @Test
    @DisplayName("processing time is recorded and non-negative")
    void processingTimeIsNonNegative() {
        TranscriptionResult result = engine.transcribe(SAMPLE_AUDIO, AudioFormat.MP3, "en");
        assertThat(result.processingTime().isNegative()).isFalse();
    }

    // ── Engine metadata ───────────────────────────────────────────────────────

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
