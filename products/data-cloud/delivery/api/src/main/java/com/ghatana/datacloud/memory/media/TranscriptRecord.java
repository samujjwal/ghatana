/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.media;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Persistent record of a transcription result from a media artifact.
 *
 * <p>A {@code TranscriptRecord} captures the transcription output along with
 * lineage information linking it back to the source media artifact. This enables
 * traceability from transcript back to the original audio/video file.
 *
 * <p>Lineage information includes:
 * <ul>
 *   <li>Source artifact ID: the media artifact that was transcribed</li>
 *   <li>Transcription tool: the tool that performed the transcription</li>
 *   <li>Language code: the language detected or specified</li>
 *   <li>Confidence score: the confidence score from the STT provider</li>
 *   <li>Processing duration: time taken to complete transcription</li>
 * </ul>
 *
 * @param transcriptId    globally unique identifier for this transcript
 * @param sourceArtifactId the media artifact that was transcribed
 * @param tenantId        tenant scope for isolation
 * @param agentId         agent that requested the transcription
 * @param transcriptText  the transcribed text
 * @param languageCode    language code (e.g., {@code en-US}, {@code es-ES})
 * @param confidence      confidence score from STT provider (0.0-1.0)
 * @param toolId          tool that performed the transcription (e.g., {@code av.speech-to-text})
 * @param processingMs    time taken to complete transcription in milliseconds
 * @param metadata        arbitrary key-value metadata for extension
 * @param createdAt       time at which this record was persisted
 *
 * @doc.type record
 * @doc.purpose Metadata record for a transcription result with lineage
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record TranscriptRecord(
        String transcriptId,
        String sourceArtifactId,
        String tenantId,
        String agentId,
        String transcriptText,
        String languageCode,
        double confidence,
        String toolId,
        long processingMs,
        Map<String, String> metadata,
        Instant createdAt) {

    public TranscriptRecord {
        Objects.requireNonNull(transcriptId, "transcriptId must not be null");
        Objects.requireNonNull(sourceArtifactId, "sourceArtifactId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(agentId, "agentId must not be null");
        Objects.requireNonNull(transcriptText, "transcriptText must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        if (transcriptId.isBlank()) throw new IllegalArgumentException("transcriptId must not be blank");
        if (sourceArtifactId.isBlank()) throw new IllegalArgumentException("sourceArtifactId must not be blank");
        if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
        if (confidence < 0.0 || confidence > 1.0) throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        if (processingMs < 0) throw new IllegalArgumentException("processingMs must not be negative");

        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Creates a new record with a generated UUID, stamped at {@code Instant.now()}.
     *
     * @param sourceArtifactId the media artifact that was transcribed
     * @param tenantId        tenant scope
     * @param agentId         agent that requested the transcription
     * @param transcriptText  the transcribed text
     * @param languageCode    language code
     * @param confidence      confidence score
     * @param toolId          tool that performed the transcription
     * @param processingMs    processing time in milliseconds
     * @param metadata        extension metadata
     * @return a new TranscriptRecord with a generated transcriptId and current timestamp
     */
    public static TranscriptRecord create(
            String sourceArtifactId,
            String tenantId,
            String agentId,
            String transcriptText,
            String languageCode,
            double confidence,
            String toolId,
            long processingMs,
            Map<String, String> metadata) {
        return new TranscriptRecord(
                UUID.randomUUID().toString(),
                sourceArtifactId, tenantId, agentId,
                transcriptText, languageCode, confidence,
                toolId, processingMs, metadata, Instant.now());
    }
}
