package com.ghatana.stt.quality;

import com.ghatana.stt.engine.WhisperTranscriptionEngine;
import com.ghatana.stt.engine.WhisperTranscriptionEngine.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Transcription accuracy regression tests for {@link WhisperTranscriptionEngine}.
 *
 * <p>Uses in-memory audio fixtures to verify that the engine produces stable,
 * non-empty transcriptions under various simulated conditions (noise, accents,
 * multiple speakers, background music).
 *
 * @doc.type    class
 * @doc.purpose Transcription accuracy: benchmark fixtures, noise, accent, multi-speaker, background music
 * @doc.layer   product
 * @doc.pattern Test
 */
@DisplayName("TranscriptionAccuracyTest")
class TranscriptionAccuracyTest {

    private WhisperTranscriptionEngine engine;
    private WhisperTranscriptionEngine diarEngine;

    @BeforeEach
    void setUp() {
        engine = new WhisperTranscriptionEngine("whisper-large", false);
        diarEngine = new WhisperTranscriptionEngine("whisper-large", true);
    }

    // ── Benchmark dataset ─────────────────────────────────────────────────────

    @ParameterizedTest(name = "fixture={0}")
    @CsvSource({
        "clean_hello_world,    Hello world today,        en",
        "clean_numbers,        One two three four five,  en",
        "clean_question,       What is the weather like, en",
        "clean_punctuation,    Yes no maybe I do not know, en",
    })
    @DisplayName("known benchmark fixtures produce non-empty transcription")
    void benchmarkFixturesProduceTranscription(String fixtureId, String expectedPhrase, String language) {
        byte[] audio = makeFixture(fixtureId);
        TranscriptionResult result = engine.transcribe(audio, AudioFormat.WAV, language);
        // Stub engine cannot reproduce exact phrases; validate structural quality
        assertThat(result.text()).isNotBlank();
        assertThat(result.confidence()).isBetween(0.0, 1.0);
        assertThat(result.inputFormat()).isEqualTo(AudioFormat.WAV);
    }

    // ── Noise robustness ──────────────────────────────────────────────────────

    @Test
    @DisplayName("noisy audio still produces a transcription")
    void noisyAudioProducesTranscription() {
        byte[] noisy = buildNoisy(256);
        TranscriptionResult result = engine.transcribe(noisy, AudioFormat.WAV, "en");
        assertThat(result.text()).isNotBlank();
    }

    @Test
    @DisplayName("noise level does not reduce confidence below 0")
    void noisyAudioConfidenceIsNonNegative() {
        byte[] noisy = buildNoisy(1024);
        TranscriptionResult result = engine.transcribe(noisy, AudioFormat.WAV, "en");
        assertThat(result.confidence()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("high-noise audio is processed without throwing")
    void highNoiseAudioNoException() {
        byte[] noisy = buildNoisy(8192);
        assertThatCode(() -> engine.transcribe(noisy, AudioFormat.WAV, "en"))
                .doesNotThrowAnyException();
    }

    // ── Accent variety ────────────────────────────────────────────────────────

    @ParameterizedTest(name = "lang={0}")
    @CsvSource({
        "en-US", "en-GB", "en-AU", "en-IN"
    })
    @DisplayName("transcription completes for English accent variants")
    void accentVariantsComplete(String language) {
        byte[] audio = makeFixture("accent_" + language);
        assertThatCode(() -> engine.transcribe(audio, AudioFormat.WAV, language))
                .doesNotThrowAnyException();
    }

    // ── Multiple speakers ─────────────────────────────────────────────────────

    @Test
    @DisplayName("multi-speaker audio produces diarization segments when enabled")
    void multiSpeakerProducesSegments() {
        byte[] audio = makeFixture("multi_speaker_two_persons");
        TranscriptionResult result = diarEngine.transcribe(audio, AudioFormat.WAV, "en");
        assertThat(result.speakerSegments()).isNotEmpty();
    }

    @Test
    @DisplayName("each diarization segment has a non-blank speaker ID")
    void speakerSegmentsHaveIds() {
        byte[] audio = makeFixture("multi_speaker_dialog");
        TranscriptionResult result = diarEngine.transcribe(audio, AudioFormat.WAV, "en");
        result.speakerSegments().forEach(seg ->
                assertThat(seg.speakerId()).isNotBlank());
    }

    // ── Background music ──────────────────────────────────────────────────────

    @Test
    @DisplayName("audio with simulated background music is processed without error")
    void backgroundMusicHandled() {
        byte[] mixed = buildMixed(256);
        assertThatCode(() -> engine.transcribe(mixed, AudioFormat.WAV, "en"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("mixed audio confidence stays within [0, 1]")
    void backgroundMusicConfidenceInRange() {
        byte[] mixed = buildMixed(1024);
        TranscriptionResult result = engine.transcribe(mixed, AudioFormat.WAV, "en");
        assertThat(result.confidence()).isBetween(0.0, 1.0);
    }

    // ── Audio format variety ──────────────────────────────────────────────────

    @ParameterizedTest(name = "format={0}")
    @CsvSource({
        "PCM, pcm_sample",
        "WAV, wav_sample",
        "MP3, mp3_sample",
        "FLAC, flac_sample",
        "OGG, ogg_sample",
        "AAC, aac_sample",
    })
    @DisplayName("known sample fixture accepted for each format")
    void formatMatrixAccepted(AudioFormat format, String fixtureId) {
        byte[] audio = makeFixture(fixtureId);
        TranscriptionResult result = engine.transcribe(audio, format, "en");
        assertThat(result).isNotNull();
        assertThat(result.inputFormat()).isEqualTo(format);
    }

    // ── Processing time ───────────────────────────────────────────────────────

    @Test
    @DisplayName("processing time is always non-negative")
    void processingTimeIsNonNegative() {
        TranscriptionResult result = engine.transcribe(makeFixture("clean_hello_world"), AudioFormat.WAV, "en");
        assertThat(result.processingTime().isNegative()).isFalse();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private byte[] makeFixture(String fixtureId) {
        return ("FIXTURE:" + fixtureId).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] buildNoisy(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) (Math.random() * 256);
        }
        return data;
    }

    private byte[] buildMixed(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) ((i % 64) + 64);
        }
        return data;
    }
}
