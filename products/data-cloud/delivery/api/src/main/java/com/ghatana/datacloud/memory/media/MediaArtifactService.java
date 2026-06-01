/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.media;

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
 * <p>WS3: Extracted from MediaArtifactController to separate business logic from HTTP concerns.
 * This service handles all media artifact operations including creation, deletion, consent updates,
 * processing job management, and transcript/frame index operations.
 *
 * <p>All mutating operations emit canonical events and record operations for audit trail.
 * Event emission and operation recording are mandatory - the service requires both dependencies.
 *
 * @doc.type class
 * @doc.purpose Service layer for media artifact business logic
 * @doc.layer product
 * @doc.pattern Service
 */
public final class MediaArtifactService {

    private final MediaArtifactRepository repository;
    private final MediaArtifactEventEmitter eventEmitter;
    private final OperationRecorder operationRecorder;

    /**
     * Creates a new MediaArtifactService with mandatory event emission and operation recording.
     *
     * @param repository the media artifact repository
     * @param eventEmitter the event emitter (mandatory)
     * @param operationRecorder the operation recorder (mandatory)
     */
    public MediaArtifactService(
            MediaArtifactRepository repository,
            MediaArtifactEventEmitter eventEmitter,
            OperationRecorder operationRecorder) {
        this.repository = repository;
        this.eventEmitter = eventEmitter;
        this.operationRecorder = operationRecorder;
    }

    /**
     * Creates a new media artifact record.
     *
     * @param record the record to create
     * @param principal the authenticated principal
     * @param request the HTTP request for operation recording
     * @return promise of the created record
     */
    public Promise<MediaArtifactRecord> createArtifact(
            MediaArtifactRecord record,
            Principal principal,
            HttpRequest request) {
        return repository.save(record)
            .then(saved -> {
                // Record operation with canonical permission context
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

                // Emit canonical event
                return eventEmitter.emitCreated(saved)
                    .map(offset -> saved);
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
                            // Record operation
                            recordMediaOperation(
                                tenantId, artifactId, record.agentId(),
                                OperationKind.MEDIA_PROCESSING, OperationStatus.SUCCEEDED,
                                "Media artifact delete", "Media artifact marked as deleted",
                                principal, request, Map.of("permission", "media:artifact:delete"));

                            // Emit canonical event
                            return eventEmitter.emitDeleted(artifactId, tenantId, record.agentId())
                                .map(offset -> true);
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
                            // Record operation
                            recordMediaOperation(
                                tenantId, artifactId, record.agentId(),
                                OperationKind.MEDIA_PROCESSING, OperationStatus.SUCCEEDED,
                                "Media artifact consent update", "Consent status updated to " + consentStatus,
                                principal, request, Map.of("consentStatus", consentStatus));

                            // Emit canonical event
                            return eventEmitter.emitUpdated(artifactId, tenantId, record.agentId(), "consent", consentStatus)
                                .map(offset -> true);
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
                    recordMediaOperation(
                        tenantId, artifactId, record.agentId(),
                        OperationKind.MEDIA_PROCESSING, OperationStatus.BLOCKED,
                        "Media processing retry", "Consent required for processing",
                        principal, request, Map.of("consentStatus", record.consentStatus()));
                    return Promise.of(false);
                }

                // Clear error and reset state
                return repository.updateLastError(artifactId, tenantId, null, principal.getName())
                    .then(cleared -> repository.updateProcessingState(artifactId, tenantId, MediaArtifactRecord.LIFECYCLE_QUEUED))
                    .then(updated -> {
                        String jobId = "retry-" + artifactId + "-" + System.currentTimeMillis();
                        recordMediaOperation(
                            tenantId, artifactId, record.agentId(),
                            OperationKind.MEDIA_PROCESSING, OperationStatus.ACCEPTED,
                            "Media processing retry", "Processing retry initiated",
                            principal, request, Map.of("jobId", jobId));

                        // Emit canonical event
                        return eventEmitter.emitProcessingRequested(artifactId, tenantId, record.agentId(), "retry", "auto")
                            .map(offset -> true);
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
                    recordMediaOperation(
                        tenantId, artifactId, record.agentId(),
                        OperationKind.MEDIA_PROCESSING, OperationStatus.BLOCKED,
                        "Media transcription", "Consent required for transcription",
                        principal, request, Map.of("consentStatus", record.consentStatus()));
                    return Promise.of(false);
                }

                // Update processing state
                return repository.updateProcessingState(artifactId, tenantId, MediaArtifactRecord.LIFECYCLE_PROCESSING)
                    .then(updated -> {
                        recordMediaOperation(
                            tenantId, artifactId, record.agentId(),
                            OperationKind.MEDIA_PROCESSING, OperationStatus.ACCEPTED,
                            "Media transcription", "Transcription triggered",
                            principal, request, Map.of("languageCode", languageCode));

                        // Emit canonical event
                        return eventEmitter.emitTranscriptionRequested(artifactId, tenantId, record.agentId(), languageCode)
                            .map(offset -> true);
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
                    recordMediaOperation(
                        tenantId, artifactId, record.agentId(),
                        OperationKind.MEDIA_PROCESSING, OperationStatus.BLOCKED,
                        "Media vision analysis", "Consent required for analysis",
                        principal, request, Map.of("consentStatus", record.consentStatus()));
                    return Promise.of(false);
                }

                // Update processing state
                return repository.updateProcessingState(artifactId, tenantId, MediaArtifactRecord.LIFECYCLE_PROCESSING)
                    .then(updated -> {
                        recordMediaOperation(
                            tenantId, artifactId, record.agentId(),
                            OperationKind.MEDIA_PROCESSING, OperationStatus.ACCEPTED,
                            "Media vision analysis", "Vision analysis triggered",
                            principal, request, Map.of("analysisType", analysisType));

                        // Emit canonical event
                        return eventEmitter.emitProcessingRequested(artifactId, tenantId, record.agentId(), "vision", analysisType)
                            .map(offset -> true);
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
            return operationRecorder.record(
                tenantId,
                kind,
                status,
                operation,
                message,
                principal.getName(),
                Map.of(
                    "artifactId", artifactId,
                    "agentId", agentId,
                    "principal", principal.getName()
                ),
                metadata);
        } catch (Exception e) {
            // Log but don't fail the operation
            return null;
        }
    }
}
