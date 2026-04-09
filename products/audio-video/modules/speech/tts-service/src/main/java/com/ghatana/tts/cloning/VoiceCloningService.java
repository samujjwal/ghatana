package com.ghatana.tts.cloning;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Voice cloning service for TTS (AV-008.1).
 *
 * <p>Extracts a compact voice embedding from one or more audio samples and uses
 * it to condition future synthesis so the output matches the speaker's vocal
 * characteristics (fundamental frequency, formant structure, speaking rate).
 *
 * <p><strong>Privacy note:</strong> Voice embeddings are stored under a randomly
 * generated clone ID and never associated with PII by this service.
 *
 * @doc.type    class
 * @doc.purpose Voice characteristic cloning from audio samples
 * @doc.layer   product
 * @doc.pattern Service
 */
public final class VoiceCloningService {

    private static final Logger LOG = LoggerFactory.getLogger(VoiceCloningService.class);

    /** Minimum number of audio samples required for reliable cloning. */
    public static final int MIN_SAMPLES = 1;

    /** Minimum total audio duration in milliseconds for reliable cloning. */
    public static final int MIN_DURATION_MS = 3_000;

    private final int embeddingDimensions;
    private final ConcurrentHashMap<String, VoiceClone> clones = new ConcurrentHashMap<>();

    private VoiceCloningService(Builder builder) {
        this.embeddingDimensions = builder.embeddingDimensions;
    }

    // ── Cloning ───────────────────────────────────────────────────────────────

    /**
     * Creates a voice clone from the supplied audio samples.
     *
     * @param samples       non-empty list of PCM audio bytes (16kHz, 16-bit mono)
     * @param sampleRateHz  sample rate of the input audio
     * @param displayLabel  human-readable label for this clone (e.g., "my-voice")
     * @return created voice clone; never {@code null}
     * @throws VoiceCloningException if samples are insufficient or invalid
     */
    public VoiceClone createClone(List<byte[]> samples, int sampleRateHz, String displayLabel) {
        Objects.requireNonNull(samples, "samples must not be null");
        Objects.requireNonNull(displayLabel, "displayLabel must not be null");

        if (samples.isEmpty()) {
            throw new VoiceCloningException("At least " + MIN_SAMPLES + " audio sample is required");
        }

        int totalBytes   = samples.stream().mapToInt(s -> s.length).sum();
        long durationMs  = estimateDurationMs(totalBytes, sampleRateHz);

        if (durationMs < MIN_DURATION_MS) {
            throw new VoiceCloningException(
                    "Insufficient audio: need at least " + MIN_DURATION_MS + "ms, got " + durationMs + "ms");
        }

        // Extract voice embedding (stub: deterministic from sample content)
        float[] embedding = extractEmbedding(samples);
        double fundamentalFrequency = estimateFundamentalFrequency(samples, sampleRateHz);
        double speakingRateWordsPerMin = estimateSpeakingRate(totalBytes, sampleRateHz);

        String cloneId = UUID.randomUUID().toString();
        VoiceClone clone = new VoiceClone(
                cloneId, displayLabel, Instant.now(),
                embedding, fundamentalFrequency, speakingRateWordsPerMin,
                samples.size(), durationMs
        );

        clones.put(cloneId, clone);
        LOG.info("Voice clone created id={} label={} duration={}ms F0={:.1f}Hz",
                cloneId, displayLabel, durationMs, fundamentalFrequency);
        return clone;
    }

    /**
     * Retrieves a previously created voice clone.
     *
     * @param cloneId clone identifier
     * @return the clone, or {@code null} if not found
     */
    public VoiceClone getClone(String cloneId) {
        return clones.get(Objects.requireNonNull(cloneId, "cloneId must not be null"));
    }

    /**
     * Removes a voice clone.
     *
     * @param cloneId clone identifier
     * @return {@code true} if the clone existed and was removed
     */
    public boolean deleteClone(String cloneId) {
        boolean removed = clones.remove(Objects.requireNonNull(cloneId)) != null;
        if (removed) LOG.info("Voice clone deleted id={}", cloneId);
        return removed;
    }

    /** Returns the number of stored voice clones. */
    public int cloneCount() { return clones.size(); }

    // ── Internal (stub implementations) ──────────────────────────────────────

    private float[] extractEmbedding(List<byte[]> samples) {
        // Stub: produce deterministic embedding from sample hash
        float[] embedding = new float[embeddingDimensions];
        int hash = Arrays.deepHashCode(samples.stream().map(s -> s).toArray());
        for (int i = 0; i < embeddingDimensions; i++) {
            embedding[i] = (float) Math.sin(hash * (i + 1) * 0.01) * 0.5f;
        }
        return embedding;
    }

    private long estimateDurationMs(int totalBytes, int sampleRateHz) {
        // 16-bit mono PCM: 2 bytes/sample
        return totalBytes == 0 ? 0 : (long) totalBytes * 1000 / (sampleRateHz * 2L);
    }

    private double estimateFundamentalFrequency(List<byte[]> samples, int sampleRateHz) {
        // Stub: deterministic F0 in typical speech range 85–255 Hz
        int hash = samples.stream().mapToInt(s -> Arrays.hashCode(s)).sum();
        return 100.0 + (Math.abs(hash) % 155);
    }

    private double estimateSpeakingRate(int totalBytes, int sampleRateHz) {
        // Stub: typical speaking rate 100–180 words/min
        long durationSec = Math.max(1, totalBytes / (sampleRateHz * 2L));
        return 130.0 + (totalBytes % 50);
    }

    // ── Nested types ──────────────────────────────────────────────────────────

    /**
     * Voice clone produced from speaker audio samples.
     *
     * @param cloneId              unique identifier
     * @param displayLabel         human-readable label
     * @param createdAt            creation timestamp
     * @param embedding            speaker embedding vector
     * @param fundamentalFrequency estimated fundamental frequency in Hz
     * @param speakingRateWpm      estimated speaking rate in words per minute
     * @param sampleCount          number of audio samples used for cloning
     * @param totalDurationMs      total audio duration used for cloning
     */
    public record VoiceClone(
            String cloneId,
            String displayLabel,
            Instant createdAt,
            float[] embedding,
            double fundamentalFrequency,
            double speakingRateWpm,
            int sampleCount,
            long totalDurationMs
    ) {}

    /** Thrown when voice cloning fails due to invalid or insufficient input. */
    public static class VoiceCloningException extends RuntimeException {
        public VoiceCloningException(String message) { super(message); }
    }

    // ── Builder ────────────────────────────────────────────────────────────────

    /** Returns a new builder. */
    public static Builder builder() { return new Builder(); }

    /**
     * Builder for {@link VoiceCloningService}.
     */
    public static final class Builder {
        private int embeddingDimensions = 256;

        private Builder() {}

        public Builder embeddingDimensions(int dims) {
            if (dims <= 0) throw new IllegalArgumentException("embeddingDimensions must be positive");
            this.embeddingDimensions = dims;
            return this;
        }

        public VoiceCloningService build() { return new VoiceCloningService(this); }
    }
}
