/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.media;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Transcript result for audio/video media artifacts.
 *
 * <p>Pass 6 - Audio-video first-class modality: Stores transcription output
 * with speaker labels, timestamps, confidence scores, and language detection.
 *
 * @param transcriptId    globally unique transcript identifier
 * @param artifactId      associated media artifact ID
 * @param tenantId        tenant scope for isolation
 * @param jobId           processing job that created this transcript
 * @param languageCode    detected language (e.g., "en-US", "es-ES")
 * @param segments        transcript segments with timing and speaker info
 * @param fullText        complete transcript text
 * @param confidence      overall confidence score (0.0 - 1.0)
 * @param durationMs      duration of transcribed audio in milliseconds
 * @param wordCount       total word count
 * @param speakerCount    number of unique speakers detected
 * @param metadata        additional metadata (model, processing time, etc.)
 * @param createdAt       transcript creation timestamp
 * @param createdBy       user ID who initiated the transcription
 *
 * @doc.type record
 * @doc.purpose Transcript result storage for Pass 6
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record Transcript(
        String transcriptId,
        String artifactId,
        String tenantId,
        String jobId,
        String languageCode,
        List<TranscriptSegment> segments,
        String fullText,
        double confidence,
        long durationMs,
        int wordCount,
        int speakerCount,
        Map<String, String> metadata,
        Instant createdAt,
        String createdBy) {

    public Transcript {
        Objects.requireNonNull(transcriptId, "transcriptId must not be null");
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(languageCode, "languageCode must not be null");
        Objects.requireNonNull(segments, "segments must not be null");
        Objects.requireNonNull(fullText, "fullText must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        if (transcriptId.isBlank()) throw new IllegalArgumentException("transcriptId must not be blank");
        if (artifactId.isBlank()) throw new IllegalArgumentException("artifactId must not be blank");
        if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        if (confidence < 0.0 || confidence > 1.0) throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        if (durationMs < 0) throw new IllegalArgumentException("durationMs must not be negative");

        segments = List.copyOf(segments);
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Creates a new transcript.
     *
     * @param artifactId   associated artifact ID
     * @param tenantId     tenant scope
     * @param jobId        processing job ID
     * @param languageCode detected language
     * @param segments     transcript segments
     * @param fullText     complete transcript text
     * @param confidence   overall confidence
     * @param durationMs   duration in milliseconds
     * @param createdBy    user ID who initiated the transcription
     * @return a new Transcript with generated ID
     */
    public static Transcript create(
            String artifactId,
            String tenantId,
            String jobId,
            String languageCode,
            List<TranscriptSegment> segments,
            String fullText,
            double confidence,
            long durationMs,
            String createdBy) {
        return new Transcript(
                UUID.randomUUID().toString(),
                artifactId,
                tenantId,
                jobId,
                languageCode,
                segments,
                fullText,
                confidence,
                durationMs,
                fullText.split("\\s+").length,
                segments.stream().map(TranscriptSegment::speakerId).distinct().mapToInt(s -> 1).sum(),
                Map.of(),
                Instant.now(),
                createdBy);
    }

    /**
     * A single segment of a transcript with timing and speaker info.
     *
     * @param segmentId  segment identifier within the transcript
     * @param startMs    start time in milliseconds
     * @param endMs      end time in milliseconds
     * @param speakerId  speaker identifier
     * @param text       spoken text
     * @param confidence confidence score for this segment (0.0 - 1.0)
     */
    public record TranscriptSegment(
            int segmentId,
            long startMs,
            long endMs,
            String speakerId,
            String text,
            double confidence) {

        public TranscriptSegment {
            if (startMs < 0) throw new IllegalArgumentException("startMs must not be negative");
            if (endMs < startMs) throw new IllegalArgumentException("endMs must not be before startMs");
            if (confidence < 0.0 || confidence > 1.0) throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
    }
}
