package com.ghatana.stt.engine;

import java.time.Duration;
import java.util.*;

/**
 * Whisper-based speech-to-text transcription engine.
 *
 * <p><b>DEPRECATED - use the managed STT adapter</b><br>
 * This direct engine is retired. Use {@link com.ghatana.audio.video.multimodal.adapter.GrpcSttClientAdapter}
 * with {@code SttMode.GRPC} or {@code SttMode.LLM_FALLBACK} for transcription.
 *
 * <p>The former in-process Whisper ONNX/JNI integration has been removed from
 * production wiring; service or managed fallback paths provide transcription.
 *
 * @doc.type    class
 * @doc.purpose Whisper STT engine (DEPRECATED - use GrpcSttClientAdapter instead)
 * @doc.layer   product
 * @doc.pattern Engine
 * @deprecated Use GrpcSttClientAdapter with SttMode.GRPC or SttMode.LLM_FALLBACK for transcription
 */
@Deprecated(since = "1.0", forRemoval = true)
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
     * <p>Input validation is enforced before any engine work is attempted:
     * null or empty {@code audioData} throws {@link TranscriptionException};
     * null {@code format} throws {@link NullPointerException}. This guarantees
     * consistent argument-contract behaviour even while the ONNX backend is absent.
     *
     * @param audioData  raw audio bytes (must not be null or empty)
     * @param format     input audio format (must not be null)
     * @param language   BCP-47 language hint (null = auto-detect)
     * @return transcription result
     * @throws TranscriptionException        if {@code audioData} is null or empty
     * @throws NullPointerException          if {@code format} is null
     * @throws UnsupportedOperationException always after validation passes because this direct engine is retired
     * @deprecated Use GrpcSttClientAdapter with SttMode.GRPC or SttMode.LLM_FALLBACK for transcription
     */
    @Deprecated
    public TranscriptionResult transcribe(byte[] audioData, AudioFormat format, String language) {
        // Validate inputs before surfacing the retired-engine signal so that
        // callers receive consistent argument-contract errors regardless of
        // engine availability (tracked in GH-90000).
        validate(audioData, format);
        throw unsupported();
    }

    /**
     * Detects the language of the given audio data.
     *
     * @param audioData raw audio bytes
     * @param format    input audio format
     * @return BCP-47 language tag
     * @throws UnsupportedOperationException always after validation passes because this direct engine is retired
     */
    public String detectLanguage(byte[] audioData, AudioFormat format) {
        validate(audioData, format);
        throw unsupported();
    }

    /** @return the model ID this engine was initialized with */
    public String getModelId() { return modelId; }

    /** @return whether speaker diarization is active */
    public boolean isDiarizationEnabled() { return diarizationEnabled; }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void validate(byte[] audioData, AudioFormat format) {
        if (audioData == null || audioData.length == 0) {
            throw new TranscriptionException("Audio data must not be null or empty");
        }
        Objects.requireNonNull(format, "Audio format must not be null");
    }

    private UnsupportedOperationException unsupported() {
        return new UnsupportedOperationException(
                "WhisperTranscriptionEngine is retired. "
                        + "Use GrpcSttClientAdapter with SttMode.GRPC or SttMode.LLM_FALLBACK for transcription. "
                        + "See: https://ghatana.dev/docs/audio-video/stt#supported-modes");
    }

    /** Thrown when transcription fails due to invalid input or model error. */
    public static class TranscriptionException extends RuntimeException {
        public TranscriptionException(String message) { super(message); }
        public TranscriptionException(String message, Throwable cause) { super(message, cause); }
    }
}
