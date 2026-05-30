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
 * <p>As a first-class Data Cloud modality, media artifacts include:
 * <ul>
 *   <li>Classification: data classification level (e.g., PUBLIC, INTERNAL, CONFIDENTIAL)</li>
 *   <li>Consent: consent status for PII/biometric data</li>
 *   <li>Retention: retention policy and expiration date</li>
 *   <li>Owner: data owner and stewardship information</li>
 *   <li>Source: provenance and lineage tracking</li>
 * </ul>
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
 * @param classification  data classification level (PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED)
 * @param consentStatus   consent status for PII/biometric data (CONSENTED, PENDING, EXPIRED, NONE)
 * @param retentionPolicy retention policy identifier
 * @param expiresAt       expiration date based on retention policy
 * @param ownerId        data owner identifier
 * @param sourceSystem   originating system or service
 * @param lineage        lineage information (parent artifact IDs, transformation chain)
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
        String classification,
        String consentStatus,
        String retentionPolicy,
        Instant expiresAt,
        String ownerId,
        String sourceSystem,
        Map<String, String> lineage,
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
        lineage = lineage != null ? Map.copyOf(lineage) : Map.of();
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
     * @param classification data classification level
     * @param consentStatus consent status for PII/biometric data
     * @param retentionPolicy retention policy identifier
     * @param expiresAt expiration date
     * @param ownerId data owner identifier
     * @param sourceSystem originating system
     * @param lineage lineage information
     * @param metadata extension metadata
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
            String classification,
            String consentStatus,
            String retentionPolicy,
            Instant expiresAt,
            String ownerId,
            String sourceSystem,
            Map<String, String> lineage,
            Map<String, String> metadata) {
        return new MediaArtifactRecord(
                UUID.randomUUID().toString(),
                tenantId, agentId, mediaType,
                storageUri, sizeBytes, checksum,
                durationMs, originToolId, correlationId,
                classification, consentStatus, retentionPolicy, expiresAt,
                ownerId, sourceSystem, lineage, metadata, Instant.now());
    }

    /**
     * Creates a media artifact record from legacy metadata-shaped callers while
     * preserving the first-class governance fields introduced on the record.
     *
     * @param tenantId tenant scope
     * @param agentId producing agent
     * @param mediaType MIME type
     * @param storageUri blob storage URI
     * @param sizeBytes artifact size
     * @param checksum SHA-256 checksum
     * @param durationMs media duration
     * @param originToolId producing tool ID
     * @param correlationId trace correlation
     * @param metadata extension metadata, including optional governance fields
     * @return a new MediaArtifactRecord with extracted governance fields
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
        Map<String, String> safeMetadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        String classification = safeMetadata.getOrDefault("classification", "INTERNAL");
        String consentStatus = safeMetadata.get("consentStatus");
        String retentionPolicy = safeMetadata.get("retentionPolicy");
        Instant expiresAt = parseOptionalInstant(safeMetadata.get("retentionUntil"));
        String ownerId = safeMetadata.getOrDefault("ownerId", agentId);
        String sourceSystem = safeMetadata.getOrDefault("sourceSystem", "media-artifact-service");
        return create(
                tenantId,
                agentId,
                mediaType,
                storageUri,
                sizeBytes,
                checksum,
                durationMs,
                originToolId,
                correlationId,
                classification,
                consentStatus,
                retentionPolicy,
                expiresAt,
                ownerId,
                sourceSystem,
                Map.of(),
                safeMetadata);
    }

    private static Instant parseOptionalInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("retentionUntil must be an ISO-8601 instant", e);
        }
    }
}
