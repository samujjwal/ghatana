/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.media;

import com.ghatana.datacloud.launcher.runtime.RuntimeProfile;
import com.ghatana.datacloud.operations.OperationKind;
import com.ghatana.datacloud.operations.OperationRecord;
import com.ghatana.datacloud.operations.OperationRecorder;
import com.ghatana.datacloud.operations.OperationStatus;
import com.ghatana.platform.governance.security.Principal;
import io.activej.http.HttpRequest;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service layer for media artifact operations.
 *
 * <p>WS3-3: Extracted from MediaArtifactController to separate business logic from HTTP concerns.
 * This service handles all media artifact operations including creation, deletion, consent updates,
 * processing job management, and transcript/frame index operations.
 *
 * <p>WS3-3: All mutating operations emit canonical events and record operations for audit trail.
 * Event emission and operation recording are mandatory in production profiles (STAGING, PRODUCTION, SOVEREIGN).
 * The service requires both dependencies and validates their presence based on the active runtime profile.
 *
 * @doc.type class
 * @doc.purpose Service layer for media artifact business logic with profile-based enforcement
 * @doc.layer product
 * @doc.pattern Service
 */
public final class MediaArtifactService {

    private final MediaArtifactRepository repository;
    private final MediaArtifactEventEmitter eventEmitter;
    private final OperationRecorder operationRecorder;
    private final RuntimeProfile runtimeProfile;

    /**
     * Creates a new MediaArtifactService with mandatory event emission and operation recording.
     *
     * <p>WS3-3: In production profiles (STAGING, PRODUCTION, SOVEREIGN), eventEmitter and
     * operationRecorder must be non-null. In LOCAL and TEST profiles, they may be null for testing.
     *
     * @param repository the media artifact repository
     * @param eventEmitter the event emitter (mandatory in production profiles)
     * @param operationRecorder the operation recorder (mandatory in production profiles)
     * @param runtimeProfile the active runtime profile
     */
    public MediaArtifactService(
            MediaArtifactRepository repository,
            MediaArtifactEventEmitter eventEmitter,
            OperationRecorder operationRecorder,
            RuntimeProfile runtimeProfile) {
        this.repository = repository;
        this.eventEmitter = eventEmitter;
        this.operationRecorder = operationRecorder;
        this.runtimeProfile = runtimeProfile;

        // WS3-3: Validate mandatory dependencies in production profiles
        if (runtimeProfile.isProduction()) {
            if (eventEmitter == null) {
                throw new IllegalStateException(
                    "MediaArtifactEventEmitter is mandatory in " + runtimeProfile + " profile. " +
                    "Event emission is required for production audit trail.");
            }
            if (operationRecorder == null) {
                throw new IllegalStateException(
                    "OperationRecorder is mandatory in " + runtimeProfile + " profile. " +
                    "Operation recording is required for production audit trail.");
            }
        }
    }

    /**
     * Creates a new media artifact record with business logic for validation and lifecycle state.
     *
     * @param tenantId the tenant ID
     * @param agentId the agent ID
     * @param mediaType the media type
     * @param storageUri the storage URI
     * @param sizeBytes the size in bytes
     * @param checksum the checksum
     * @param durationMs the duration in milliseconds
     * @param originToolId the origin tool ID
     * @param correlationId the correlation ID
     * @param consentStatus the consent status
     * @param retentionPolicy the retention policy
     * @param retentionUntil the retention until date
     * @param metadata additional metadata
     * @param principal the authenticated principal
     * @param request the HTTP request for operation recording
     * @return promise of the created record
     */
    public Promise<MediaArtifactRecord> createArtifact(
            String tenantId,
            String agentId,
            String mediaType,
            String storageUri,
            Long sizeBytes,
            String checksum,
            Long durationMs,
            String originToolId,
            String correlationId,
            String consentStatus,
            String retentionPolicy,
            String retentionUntil,
            Map<String, String> metadata,
            Principal principal,
            HttpRequest request) {
        // Validate required fields
        if (agentId == null || mediaType == null || storageUri == null) {
            throw new IllegalArgumentException("agentId, mediaType, and storageUri are required");
        }

        // Validate consent requirements
        if (requiresExplicitConsent(mediaType) && consentStatus == null) {
            throw new IllegalArgumentException("consentStatus is required for audio/video media artifacts");
        }

        // Build metadata map
        Map<String, String> finalMetadata = new java.util.HashMap<>();
        if (metadata != null) {
            finalMetadata.putAll(metadata);
        }
        if (consentStatus != null) {
            finalMetadata.put("consentStatus", consentStatus);
        }
        if (retentionPolicy != null) {
            finalMetadata.put("retentionPolicy", retentionPolicy);
        }
        if (retentionUntil != null) {
            finalMetadata.put("retentionUntil", retentionUntil);
        }

        // Determine initial lifecycle state based on consent
        String processingState = MediaArtifactRecord.LIFECYCLE_REGISTERED;
        if (requiresExplicitConsent(mediaType)) {
            processingState = consentStatus != null && consentStatus.equals(MediaArtifactRecord.CONSENT_GRANTED)
                ? MediaArtifactRecord.LIFECYCLE_QUEUED
                : MediaArtifactRecord.LIFECYCLE_CONSENT_PENDING;
        }

        // Create record
        MediaArtifactRecord record = MediaArtifactRecord.create(
            tenantId,
            agentId,
            mediaType,
            storageUri,
            sizeBytes != null ? sizeBytes : 0L,
            checksum,
            durationMs != null ? durationMs : 0L,
            originToolId,
            correlationId,
            "ACTIVE",
            processingState,
            "INTERNAL",
            consentStatus,
            retentionPolicy,
            null,
            null,
            agentId,
            "media-artifact-service",
            Map.of(),
            Map.copyOf(finalMetadata),
            principal.getName());

        return repository.save(record)
            .then(saved -> {
                // WS3-3: Record operation if operation recorder is available (mandatory in production)
                if (operationRecorder != null) {
                    OperationRecord operation = recordMediaOperation(
                        saved.tenantId(),
                        saved.artifactId(),
                        saved.agentId(),
                        OperationKind.MEDIA_PROCESSING,
                        OperationStatus.SUCCEEDED,
                        "Media artifact create",
                        "Media artifact created successfully",
                        principal,
                        request,
                        Map.of("permission", "media:artifact:create", "mediaType", saved.mediaType(), "operation", "create"));
                }

                // WS3-3: Emit canonical event if event emitter is available (mandatory in production)
                if (eventEmitter != null) {
                    return eventEmitter.emitCreated(saved)
                        .map(offset -> saved);
                }
                return Promise.of(saved);
            });
    }

    /**
     * Finds a media artifact by ID.
     *
     * @param artifactId the artifact ID
     * @param tenantId the tenant ID
     * @return promise of the record, empty if not found
     */
    public Promise<Optional<MediaArtifactRecord>> getArtifact(String artifactId, String tenantId) {
        return repository.findById(artifactId, tenantId);
    }

    // Helper methods moved from controller

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean requiresExplicitConsent(String mediaType) {
        return mediaType.startsWith("audio/") || mediaType.startsWith("video/");
    }

    /**
     * Lists media artifacts by agent or media type.
     *
     * @param agentId optional agent ID filter
     * @param mediaType optional media type filter
     * @param tenantId the tenant ID
     * @param limit maximum results
     * @return promise of matching records
     */
    public Promise<List<MediaArtifactRecord>> listArtifacts(
            String agentId,
            String mediaType,
            String tenantId,
            int limit) {
        if (agentId != null) {
            return repository.findByAgent(agentId, tenantId, limit);
        }
        if (mediaType != null) {
            return repository.findByMediaType(mediaType, tenantId, limit);
        }
        return Promise.of(List.of());
    }

    /**
     * Deletes a media artifact (soft delete).
     *
     * @param artifactId the artifact ID
     * @param tenantId the tenant ID
     * @param principal the authenticated principal
     * @param request the HTTP request for operation recording
     * @return promise completing when deleted
     */
    public Promise<Boolean> deleteArtifact(
            String artifactId,
            String tenantId,
            Principal principal,
            HttpRequest request) {
        return repository.findById(artifactId, tenantId)
            .then(optional -> {
                if (optional.isEmpty()) {
                    return Promise.of(false);
                }

                MediaArtifactRecord record = optional.get();

                // Enforce retention policy
                if (!record.isRetentionPolicyValid()) {
                    OperationRecord operation = recordMediaOperation(
                        tenantId, artifactId, record.agentId(),
                        OperationKind.MEDIA_PROCESSING, OperationStatus.BLOCKED,
                        "Media artifact delete", "Retention policy prevents deletion",
                        principal, request, Map.of("retentionPolicy", record.retentionPolicy()));
                    return Promise.of(false);
                }

                // Soft delete
                return repository.markDeleted(artifactId, tenantId, principal.getName())
                    .then(deleted -> {
                        if (deleted) {
                            // WS3-3: Record operation if operation recorder is available (mandatory in production)
                            if (operationRecorder != null) {
                                recordMediaOperation(
                                    tenantId, artifactId, record.agentId(),
                                    OperationKind.MEDIA_PROCESSING, OperationStatus.SUCCEEDED,
                                    "Media artifact delete", "Media artifact marked as deleted",
                                    principal, request, Map.of("permission", "media:artifact:delete"));
                            }

                            // WS3-3: Emit canonical event if event emitter is available (mandatory in production)
                            if (eventEmitter != null) {
                                return eventEmitter.emitDeleted(artifactId, tenantId, record.agentId())
                                    .map(offset -> true);
                            }
                            return Promise.of(true);
                        }
                        return Promise.of(false);
                    });
            });
    }

    /**
     * Updates consent status for a media artifact.
     *
     * @param artifactId the artifact ID
     * @param tenantId the tenant ID
     * @param consentStatus the new consent status
     * @param principal the authenticated principal
     * @param request the HTTP request for operation recording
     * @return promise completing when updated
     */
    public Promise<Boolean> updateConsentStatus(
            String artifactId,
            String tenantId,
            String consentStatus,
            Principal principal,
            HttpRequest request) {
        return repository.findById(artifactId, tenantId)
            .then(optional -> {
                if (optional.isEmpty()) {
                    return Promise.of(false);
                }

                MediaArtifactRecord record = optional.get();

                return repository.updateConsentStatus(artifactId, tenantId, consentStatus, principal.getName())
                    .then(updated -> {
                        if (updated) {
                            // WS3-3: Record operation if operation recorder is available (mandatory in production)
                            if (operationRecorder != null) {
                                recordMediaOperation(
                                    tenantId, artifactId, record.agentId(),
                                    OperationKind.MEDIA_PROCESSING, OperationStatus.SUCCEEDED,
                                    "Media artifact consent update", "Consent status updated to " + consentStatus,
                                    principal, request, Map.of("consentStatus", consentStatus));
                            }

                            // WS3-3: Emit canonical event if event emitter is available (mandatory in production)
                            if (eventEmitter != null) {
                                return eventEmitter.emitUpdated(artifactId, tenantId, record.agentId(), "consent", consentStatus)
                                    .map(offset -> true);
                            }
                            return Promise.of(true);
                        }
                        return Promise.of(false);
                    });
            });
    }

    /**
     * Gets processing jobs for a media artifact.
     *
     * @param artifactId the artifact ID
     * @param tenantId the tenant ID
     * @return promise of job records
     */
    public Promise<List<MediaProcessingJob>> getJobs(String artifactId, String tenantId) {
        return repository.findJobs(artifactId, tenantId);
    }

    /**
     * Gets transcript for a media artifact.
     *
     * @param artifactId the artifact ID
     * @param tenantId the tenant ID
     * @return promise of the transcript, empty if not found
     */
    public Promise<Optional<Transcript>> getTranscript(String artifactId, String tenantId) {
        return repository.findTranscript(artifactId, tenantId);
    }

    /**
     * Gets frame index for a media artifact.
     *
     * @param artifactId the artifact ID
     * @param tenantId the tenant ID
     * @return promise of the frame index, empty if not found
     */
    public Promise<Optional<FrameIndex>> getFrameIndex(String artifactId, String tenantId) {
        return repository.findFrameIndex(artifactId, tenantId);
    }

    /**
     * Retries processing for a failed media artifact.
     *
     * @param artifactId the artifact ID
     * @param tenantId the tenant ID
     * @param principal the authenticated principal
     * @param request the HTTP request for operation recording
     * @return promise completing when retry is initiated
     */
    public Promise<Boolean> retryProcessing(
            String artifactId,
            String tenantId,
            Principal principal,
            HttpRequest request) {
        return repository.findById(artifactId, tenantId)
            .then(optional -> {
                if (optional.isEmpty()) {
                    return Promise.of(false);
                }

                MediaArtifactRecord record = optional.get();

                // Only retry failed artifacts
                if (!MediaArtifactRecord.LIFECYCLE_FAILED.equals(record.processingState())) {
                    return Promise.of(false);
                }

                // Enforce consent
                if (!record.hasConsentForProcessing()) {
                    if (operationRecorder != null) {
                        recordMediaOperation(
                            tenantId, artifactId, record.agentId(),
                            OperationKind.MEDIA_PROCESSING, OperationStatus.BLOCKED,
                            "Media processing retry", "Consent required for processing",
                            principal, request, Map.of("consentStatus", record.consentStatus()));
                    }
                    return Promise.of(false);
                }

                // Clear error and reset state
                return repository.updateLastError(artifactId, tenantId, null, principal.getName())
                    .then(cleared -> repository.updateProcessingState(artifactId, tenantId, MediaArtifactRecord.LIFECYCLE_QUEUED))
                    .then(updated -> {
                        String jobId = "retry-" + artifactId + "-" + System.currentTimeMillis();
                        if (operationRecorder != null) {
                            recordMediaOperation(
                                tenantId, artifactId, record.agentId(),
                                OperationKind.MEDIA_PROCESSING, OperationStatus.ACCEPTED,
                                "Media processing retry", "Processing retry initiated",
                                principal, request, Map.of("jobId", jobId));
                        }

                        // WS3-3: Emit canonical event if event emitter is available (mandatory in production)
                        if (eventEmitter != null) {
                            return eventEmitter.emitProcessingRequested(artifactId, tenantId, record.agentId(), "retry", "auto")
                                .map(offset -> true);
                        }
                        return Promise.of(true);
                    });
            });
    }

    /**
     * Triggers transcription for a media artifact.
     *
     * @param artifactId the artifact ID
     * @param tenantId the tenant ID
     * @param languageCode the language code for transcription
     * @param principal the authenticated principal
     * @param request the HTTP request for operation recording
     * @return promise completing when transcription is triggered
     */
    public Promise<Boolean> triggerTranscription(
            String artifactId,
            String tenantId,
            String languageCode,
            Principal principal,
            HttpRequest request) {
        return repository.findById(artifactId, tenantId)
            .then(optional -> {
                if (optional.isEmpty()) {
                    return Promise.of(false);
                }

                MediaArtifactRecord record = optional.get();

                // Enforce consent
                if (!record.hasConsentForProcessing()) {
                    if (operationRecorder != null) {
                        recordMediaOperation(
                            tenantId, artifactId, record.agentId(),
                            OperationKind.MEDIA_PROCESSING, OperationStatus.BLOCKED,
                            "Media transcription", "Consent required for transcription",
                            principal, request, Map.of("consentStatus", record.consentStatus()));
                    }
                    return Promise.of(false);
                }

                // Update processing state
                return repository.updateProcessingState(artifactId, tenantId, MediaArtifactRecord.LIFECYCLE_PROCESSING)
                    .then(updated -> {
                        if (operationRecorder != null) {
                            recordMediaOperation(
                                tenantId, artifactId, record.agentId(),
                                OperationKind.MEDIA_PROCESSING, OperationStatus.ACCEPTED,
                                "Media transcription", "Transcription triggered",
                                principal, request, Map.of("languageCode", languageCode));
                        }

                        // WS3-3: Emit canonical event if event emitter is available (mandatory in production)
                        if (eventEmitter != null) {
                            return eventEmitter.emitTranscriptionRequested(artifactId, tenantId, record.agentId(), languageCode)
                                .map(offset -> true);
                        }
                        return Promise.of(true);
                    });
            });
    }

    /**
     * Triggers vision analysis for a media artifact.
     *
     * @param artifactId the artifact ID
     * @param tenantId the tenant ID
     * @param analysisType the type of vision analysis
     * @param principal the authenticated principal
     * @param request the HTTP request for operation recording
     * @return promise completing when analysis is triggered
     */
    public Promise<Boolean> triggerVisionAnalysis(
            String artifactId,
            String tenantId,
            String analysisType,
            Principal principal,
            HttpRequest request) {
        return repository.findById(artifactId, tenantId)
            .then(optional -> {
                if (optional.isEmpty()) {
                    return Promise.of(false);
                }

                MediaArtifactRecord record = optional.get();

                // Enforce consent
                if (!record.hasConsentForProcessing()) {
                    if (operationRecorder != null) {
                        recordMediaOperation(
                            tenantId, artifactId, record.agentId(),
                            OperationKind.MEDIA_PROCESSING, OperationStatus.BLOCKED,
                            "Media vision analysis", "Consent required for analysis",
                            principal, request, Map.of("consentStatus", record.consentStatus()));
                    }
                    return Promise.of(false);
                }

                // Update processing state
                return repository.updateProcessingState(artifactId, tenantId, MediaArtifactRecord.LIFECYCLE_PROCESSING)
                    .then(updated -> {
                        if (operationRecorder != null) {
                            recordMediaOperation(
                                tenantId, artifactId, record.agentId(),
                                OperationKind.MEDIA_PROCESSING, OperationStatus.ACCEPTED,
                                "Media vision analysis", "Vision analysis triggered",
                                principal, request, Map.of("analysisType", analysisType));
                        }

                        // WS3-3: Emit canonical event if event emitter is available (mandatory in production)
                        if (eventEmitter != null) {
                            return eventEmitter.emitProcessingRequested(artifactId, tenantId, record.agentId(), "vision", analysisType)
                                .map(offset -> true);
                        }
                        return Promise.of(true);
                    });
            });
    }

    /**
     * Triggers multimodal indexing for a media artifact.
     *
     * <p>WS3: Validates consent and retention policy before triggering indexing.
     * Emits canonical event and records operation for audit trail.
     *
     * @param artifactId the artifact ID
     * @param tenantId the tenant ID
     * @param indexType the index type (e.g., SEMANTIC, VECTOR)
     * @param principal the authenticated principal
     * @param request the HTTP request for operation recording
     * @return promise completing when indexing is triggered
     */
    public Promise<Boolean> triggerMultimodalIndexing(
            String artifactId,
            String tenantId,
            String indexType,
            Principal principal,
            HttpRequest request) {
        return repository.findById(artifactId, tenantId)
            .then(optional -> {
                if (optional.isEmpty()) {
                    return Promise.of(false);
                }

                MediaArtifactRecord record = optional.get();

                // Enforce consent
                if (!record.hasConsentForProcessing()) {
                    if (operationRecorder != null) {
                        recordMediaOperation(
                            tenantId, artifactId, record.agentId(),
                            OperationKind.MEDIA_PROCESSING, OperationStatus.BLOCKED,
                            "Media multimodal indexing", "Consent required for indexing",
                            principal, request, Map.of("consentStatus", record.consentStatus()));
                    }
                    return Promise.of(false);
                }

                // Update processing state
                return repository.updateProcessingState(artifactId, tenantId, MediaArtifactRecord.LIFECYCLE_PROCESSING)
                    .then(updated -> {
                        if (operationRecorder != null) {
                            recordMediaOperation(
                                tenantId, artifactId, record.agentId(),
                                OperationKind.MEDIA_PROCESSING, OperationStatus.ACCEPTED,
                                "Media multimodal indexing", "Multimodal indexing triggered",
                                principal, request, Map.of("indexType", indexType));
                        }

                        // WS3-3: Emit canonical event if event emitter is available (mandatory in production)
                        if (eventEmitter != null) {
                            return eventEmitter.emitProcessingRequested(artifactId, tenantId, record.agentId(), "multimodal", indexType)
                                .map(offset -> true);
                        }
                        return Promise.of(true);
                    });
            });
    }

    /**
     * Records a media operation for audit trail.
     */
    private OperationRecord recordMediaOperation(
            String tenantId,
            String artifactId,
            String agentId,
            OperationKind kind,
            OperationStatus status,
            String operation,
            String message,
            Principal principal,
            HttpRequest request,
            Map<String, String> metadata) {
        if (operationRecorder == null) {
            return null;
        }

        try {
            OperationRecord record = OperationRecord.create(
                tenantId,
                kind,
                status,
                "media-artifact",
                artifactId,
                operation,
                message,
                principal.getName(),
                null, // correlationId
                true, // cancellable
                Map.of(
                    "artifactId", artifactId,
                    "agentId", agentId,
                    "principal", principal.getName()
                )
            );
            return operationRecorder.record(record);
        } catch (Exception e) {
            // Log but don't fail the operation
            return null;
        }
    }
}
