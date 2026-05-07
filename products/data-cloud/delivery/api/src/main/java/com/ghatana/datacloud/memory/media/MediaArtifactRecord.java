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
 * Persistent record of a media artifact stored in the Data Cloud media store.
 *
 * <p>A {@code MediaArtifactRecord} captures all metadata required to locate,
 * verify, and govern access to a stored media artifact (audio, video, or image).
 * The artifact bytes themselves are stored in the configured blob backend; this
 * record holds only the metadata needed for retrieval and governance.
 *
 * @param artifactId      globally unique identifier for this artifact
 * @param tenantId        tenant scope for isolation
 * @param agentId         agent that produced or ingested this artifact
 * @param mediaType       MIME type of the stored media (e.g. {@code audio/wav}, {@code video/mp4})
 * @param storageUri      URI pointing to the artifact in blob storage
 * @param sizeBytes       size of the stored artifact in bytes
 * @param checksum        SHA-256 hex checksum of the artifact bytes (for integrity)
 * @param durationMs      duration of the media in milliseconds (0 for images)
 * @param originToolId    tool that produced this artifact (e.g. {@code av.speech-to-text})
 * @param correlationId   trace/correlation ID from the producing invocation
 * @param metadata        arbitrary key-value metadata for extension
 * @param createdAt       time at which this record was persisted
 *
 * @doc.type record
 * @doc.purpose Metadata record for a media artifact stored in Data Cloud
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record MediaArtifactRecord(
        String artifactId,
        String tenantId,
        String agentId,
        String mediaType,
        String storageUri,
        long sizeBytes,
        String checksum,
        long durationMs,
        String originToolId,
        String correlationId,
        Map<String, String> metadata,
        Instant createdAt) {

    public MediaArtifactRecord {
        Objects.requireNonNull(artifactId,   "artifactId must not be null");
        Objects.requireNonNull(tenantId,     "tenantId must not be null");
        Objects.requireNonNull(agentId,      "agentId must not be null");
        Objects.requireNonNull(mediaType,    "mediaType must not be null");
        Objects.requireNonNull(storageUri,   "storageUri must not be null");
        Objects.requireNonNull(createdAt,    "createdAt must not be null");

        if (artifactId.isBlank()) throw new IllegalArgumentException("artifactId must not be blank");
        if (tenantId.isBlank())   throw new IllegalArgumentException("tenantId must not be blank");
        if (mediaType.isBlank())  throw new IllegalArgumentException("mediaType must not be blank");
        if (sizeBytes < 0)        throw new IllegalArgumentException("sizeBytes must not be negative");

        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Creates a new record with a generated UUID, stamped at {@code Instant.now()}.
     *
     * @param tenantId      tenant scope
     * @param agentId       producing agent
     * @param mediaType     MIME type
     * @param storageUri    blob storage URI
     * @param sizeBytes     artifact size
     * @param checksum      SHA-256 checksum
     * @param durationMs    media duration (0 for images)
     * @param originToolId  producing tool ID
     * @param correlationId trace correlation
     * @param metadata      extension metadata
     * @return a new MediaArtifactRecord with a generated artifactId and current timestamp
     */
    public static MediaArtifactRecord create(
            String tenantId,
            String agentId,
            String mediaType,
            String storageUri,
            long sizeBytes,
            String checksum,
            long durationMs,
            String originToolId,
            String correlationId,
            Map<String, String> metadata) {
        return new MediaArtifactRecord(
                UUID.randomUUID().toString(),
                tenantId, agentId, mediaType,
                storageUri, sizeBytes, checksum,
                durationMs, originToolId, correlationId,
                metadata, Instant.now());
    }
}
