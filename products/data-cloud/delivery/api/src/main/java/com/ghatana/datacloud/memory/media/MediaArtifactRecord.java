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
 *   <li>Processing: job lifecycle with transcript and frame index tracking (Pass 6)</li>
 * </ul>
 *
 * <p>Pass 6 - Audio-video first-class modality lifecycle states:
 * REGISTERED, CONSENT_PENDING, CONSENT_DENIED, QUEUED, PROCESSING,
 * TRANSCRIBED, ANALYZED, INDEXED, FAILED, RETAINED, DELETED
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
 * @param status          artifact lifecycle status (ACTIVE, ARCHIVED, DELETED, EXPIRED)
 * @param processingState processing state for async operations (P6: REGISTERED, CONSENT_PENDING, CONSENT_DENIED, QUEUED, PROCESSING, TRANSCRIBED, ANALYZED, INDEXED, FAILED, RETAINED, DELETED)
 * @param classification  data classification level (PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED)
 * @param consentStatus   consent status for PII/biometric data (CONSENTED, PENDING, EXPIRED, NONE)
 * @param retentionPolicy retention policy identifier
 * @param redactionPolicy redaction policy identifier for PII handling
 * @param expiresAt       expiration date based on retention policy
 * @param ownerId        data owner identifier
 * @param sourceSystem   originating system or service
 * @param lineage        lineage information (parent artifact IDs, transformation chain)
 * @param metadata        arbitrary key-value metadata for extension
 * @param createdAt       time at which this record was persisted
 * @param updatedAt       time at which this record was last updated (P6)
 * @param processingJobId active processing job ID (P6)
 * @param transcriptId    associated transcript ID (P6)
 * @param frameIndexId    associated frame index ID (P6)
 * @param lastError       last error message if processing failed (P6)
 * @param createdBy       user ID who created this record (P6)
 * @param updatedBy       user ID who last updated this record (P6)
 * @param deletedAt       soft deletion timestamp (P6)
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
        String status,
        String processingState,
        String classification,
        String consentStatus,
        String retentionPolicy,
        String redactionPolicy,
        Instant expiresAt,
        String ownerId,
        String sourceSystem,
        Map<String, String> lineage,
        Map<String, String> metadata,
        Instant createdAt,
        // Pass 6 - Audio-video first-class modality fields
        Instant updatedAt,
        String processingJobId,
        String transcriptId,
        String frameIndexId,
        String lastError,
        String createdBy,
        String updatedBy,
        Instant deletedAt) {

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

    // Pass 6 - Lifecycle state constants
    public static final String LIFECYCLE_REGISTERED = "REGISTERED";
    public static final String LIFECYCLE_CONSENT_PENDING = "CONSENT_PENDING";
    public static final String LIFECYCLE_CONSENT_DENIED = "CONSENT_DENIED";
    public static final String LIFECYCLE_QUEUED = "QUEUED";
    public static final String LIFECYCLE_PROCESSING = "PROCESSING";
    public static final String LIFECYCLE_TRANSCRIBED = "TRANSCRIBED";
    public static final String LIFECYCLE_ANALYZED = "ANALYZED";
    public static final String LIFECYCLE_INDEXED = "INDEXED";
    public static final String LIFECYCLE_FAILED = "FAILED";
    public static final String LIFECYCLE_RETAINED = "RETAINED";
    public static final String LIFECYCLE_DELETED = "DELETED";

    // Pass 6 - Consent status constants
    public static final String CONSENT_GRANTED = "GRANTED";
    public static final String CONSENT_PENDING = "PENDING";
    public static final String CONSENT_DENIED = "DENIED";
    public static final String CONSENT_NOT_REQUIRED = "NOT_REQUIRED";

    /**
     * Returns true if the artifact has explicit consent for processing.
     * Audio/video media requires consent before transcription or analysis.
     */
    public boolean hasConsentForProcessing() {
        return CONSENT_GRANTED.equals(consentStatus) || CONSENT_NOT_REQUIRED.equals(consentStatus);
    }

    /**
     * Returns true if the artifact requires explicit consent (audio/video media).
     */
    public boolean requiresExplicitConsent() {
        return mediaType != null && (mediaType.startsWith("audio/") || mediaType.startsWith("video/"));
    }

    /**
     * Returns true if the retention policy allows deletion or processing.
     */
    public boolean isRetentionPolicyValid() {
        if (expiresAt == null) return true;
        return Instant.now().isBefore(expiresAt);
    }

    /**
     * Returns true if the artifact is in a terminal lifecycle state.
     */
    public boolean isTerminalState() {
        return LIFECYCLE_FAILED.equals(processingState)
            || LIFECYCLE_RETAINED.equals(processingState)
            || LIFECYCLE_DELETED.equals(processingState);
    }

    /**
     * Returns true if the artifact can be processed (transcribed/analyzed).
     */
    public boolean canBeProcessed() {
        return hasConsentForProcessing()
            && isRetentionPolicyValid()
            && !isTerminalState()
            && deletedAt == null;
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
     * @param status        artifact lifecycle status (defaults to ACTIVE)
     * @param processingState processing state for async operations (defaults to null)
     * @param classification data classification level
     * @param consentStatus consent status for PII/biometric data
     * @param retentionPolicy retention policy identifier
     * @param redactionPolicy redaction policy identifier for PII handling
     * @param expiresAt expiration date
     * @param ownerId data owner identifier
     * @param sourceSystem originating system
     * @param lineage lineage information
     * @param metadata extension metadata
     * @param createdBy user ID who created this record (P6)
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
            String status,
            String processingState,
            String classification,
            String consentStatus,
            String retentionPolicy,
            String redactionPolicy,
            Instant expiresAt,
            String ownerId,
            String sourceSystem,
            Map<String, String> lineage,
            Map<String, String> metadata,
            String createdBy) {
        Instant now = Instant.now();
        return new MediaArtifactRecord(
                UUID.randomUUID().toString(),
                tenantId, agentId, mediaType,
                storageUri, sizeBytes, checksum,
                durationMs, originToolId, correlationId,
                status != null ? status : "ACTIVE",
                processingState != null ? processingState : LIFECYCLE_REGISTERED,
                classification, consentStatus, retentionPolicy, redactionPolicy, expiresAt,
                ownerId, sourceSystem, lineage, metadata, now,
                // Pass 6 fields
                now,  // updatedAt
                null, // processingJobId
                null, // transcriptId
                null, // frameIndexId
                null, // lastError
                createdBy,  // createdBy
                createdBy,  // updatedBy
                null); // deletedAt
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
     * @param createdBy user ID who created this record (P6)
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
            Map<String, String> metadata,
            String createdBy) {
        Map<String, String> safeMetadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        String classification = safeMetadata.getOrDefault("classification", "INTERNAL");
        String consentStatus = safeMetadata.get("consentStatus");
        String retentionPolicy = safeMetadata.get("retentionPolicy");
        String redactionPolicy = safeMetadata.get("redactionPolicy");
        Instant expiresAt = parseOptionalInstant(safeMetadata.get("retentionUntil"));
        String ownerId = safeMetadata.getOrDefault("ownerId", agentId);
        String sourceSystem = safeMetadata.getOrDefault("sourceSystem", "media-artifact-service");
        String status = safeMetadata.get("status");
        String processingState = safeMetadata.get("processingState");
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
                status,
                processingState,
                classification,
                consentStatus,
                retentionPolicy,
                redactionPolicy,
                expiresAt,
                ownerId,
                sourceSystem,
                Map.of(),
                safeMetadata,
                createdBy);
    }

    /**
     * Creates a media artifact record from legacy metadata-shaped callers.
     * Backward-compatible version without createdBy (defaults to agentId).
     *
     * @deprecated Use {@link #create(String, String, String, String, long, String, long, String, String, Map, String)} instead
     */
    @Deprecated
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
        return create(tenantId, agentId, mediaType, storageUri, sizeBytes, checksum,
                durationMs, originToolId, correlationId, metadata, agentId);
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
