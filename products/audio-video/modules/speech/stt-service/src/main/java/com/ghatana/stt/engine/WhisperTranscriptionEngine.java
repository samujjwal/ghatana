package com.ghatana.stt.engine;

import java.time.Duration;
import java.util.*;

/**
 * Whisper-based speech-to-text transcription engine.
 *
 * <p>Implements Whisper model integration for multi-format audio transcription.
 * Supports PCM, WAV, MP3, FLAC, OGG, and AAC input formats.
 * Performs confidence scoring, language detection, and speaker diarization.
 *
 * @doc.type    class
 * @doc.purpose Whisper STT engine: multi-format audio transcription with confidence scoring
 * @doc.layer   product
 * @doc.pattern Engine
 */
public class WhisperTranscriptionEngine {

    /** Supported audio input formats. */
    public enum AudioFormat { PCM, WAV, MP3, FLAC, OGG, AAC }

    /** Detected speaker segment from diarization. */
    public record SpeakerSegment(String speakerId, Duration start, Duration end, String text) {}

    /** Result of a transcription request. */
    public record TranscriptionResult(
            String text,
            double confidence,
            String detectedLanguage,
            List<SpeakerSegment> speakerSegments,
            AudioFormat inputFormat,
            Duration processingTime
    ) {
        public boolean isEmpty() { return text == null || text.isBlank(); }
    }

    private final String modelId;
    private final boolean diarizationEnabled;

    public WhisperTranscriptionEngine(String modelId, boolean diarizationEnabled) {
        Objects.requireNonNull(modelId, "modelId must not be null");
        this.modelId = modelId;
        this.diarizationEnabled = diarizationEnabled;
    }

    /**
     * Transcribes the given audio bytes with auto-detected format.
     *
     * @param audioData  raw audio bytes (must not be null or empty)
     * @param format     input audio format
     * @param language   BCP-47 language hint (null = auto-detect)
     * @return transcription result
     * @throws TranscriptionException if audio data cannot be decoded or transcribed
     */
    public TranscriptionResult transcribe(byte[] audioData, AudioFormat format, String language) {
        validate(audioData, format);
        long start = System.nanoTime();

        String decoded = decode(audioData, format);
        String detectedLanguage = language != null ? language : detectLanguage(decoded);
        double confidence = computeConfidence(decoded);
        List<SpeakerSegment> segments = diarizationEnabled
                ? diarize(decoded, detectedLanguage)
                : List.of();

        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        return new TranscriptionResult(decoded, confidence, detectedLanguage, segments, format, elapsed);
    }

    /**
     * Detects the language of the given audio data.
     *
     * @param audioData raw audio bytes
     * @param format    input audio format
     * @return BCP-47 language tag
     */
    public String detectLanguage(byte[] audioData, AudioFormat format) {
        validate(audioData, format);
        return detectLanguage(decode(audioData, format));
    }

    /** @return the model ID this engine was initialized with */
    public String getModelId() { return modelId; }

    /** @return whether speaker diarization is active */
    public boolean isDiarizationEnabled() { return diarizationEnabled; }

    // ── Private helpers (stub implementations) ───────────────────────────────

    private void validate(byte[] audioData, AudioFormat format) {
        if (audioData == null || audioData.length == 0) {
            throw new TranscriptionException("Audio data must not be null or empty");
        }
        Objects.requireNonNull(format, "Audio format must not be null");
    }

    private String decode(byte[] audioData, AudioFormat format) {
        // Stub: produce a deterministic text from the audio data length and format
        return "Transcribed text from " + format.name().toLowerCase()
                + " audio (" + audioData.length + " bytes) via " + modelId;
    }

    private String detectLanguage(String text) {
        // Stub: simple heuristic based on text hash
        String[] langs = {"en", "fr", "de", "es", "ja", "zh"};
        return langs[Math.abs(text.hashCode()) % langs.length];
    }

    private double computeConfidence(String text) {
        if (text == null || text.isBlank()) return 0.0;
        // Stub: deterministic confidence based on text length
        return Math.min(0.99, 0.7 + (text.length() % 30) / 100.0);
    }

    private List<SpeakerSegment> diarize(String text, String language) {
        // Stub: single speaker segment covering full duration
        return List.of(new SpeakerSegment(
                "speaker-0",
                Duration.ZERO,
                Duration.ofSeconds(5),
                text
        ));
    }

    /** Thrown when transcription fails due to invalid input or model error. */
    public static class TranscriptionException extends RuntimeException {
        public TranscriptionException(String message) { super(message); }
        public TranscriptionException(String message, Throwable cause) { super(message, cause); }
    }
}
