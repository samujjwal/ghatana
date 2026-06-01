/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.media;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * SPI for persisting and retrieving {@link MediaArtifactRecord} instances.
 *
 * <p>Implementations must enforce tenant isolation: all operations are scoped
 * to the provided {@code tenantId} and must not expose records across tenants.
 *
 * @doc.type interface
 * @doc.purpose SPI for media artifact persistence
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface MediaArtifactRepository {

    /**
     * Persists a new media artifact record.
     *
     * @param record the record to save; must not be null
     * @return promise of the persisted record
     */
    Promise<MediaArtifactRecord> save(MediaArtifactRecord record);

    /**
     * Finds a media artifact by its unique artifact ID, scoped to the given tenant.
     *
     * @param artifactId the artifact identifier
     * @param tenantId   the tenant scope
     * @return promise of the record, empty if not found
     */
    Promise<Optional<MediaArtifactRecord>> findById(String artifactId, String tenantId);

    /**
     * Lists all media artifacts produced by a specific agent within a tenant.
     *
     * @param agentId  the agent identifier
     * @param tenantId the tenant scope
     * @param limit    maximum number of results
     * @return promise of matching records
     */
    Promise<List<MediaArtifactRecord>> findByAgent(String agentId, String tenantId, int limit);

    /**
     * Lists all media artifacts of a given MIME type within a tenant.
     *
     * @param mediaType the MIME type filter (e.g. {@code "audio/wav"})
     * @param tenantId  the tenant scope
     * @param limit     maximum number of results
     * @return promise of matching records
     */
    Promise<List<MediaArtifactRecord>> findByMediaType(String mediaType, String tenantId, int limit);

    /**
     * Deletes a media artifact record. Does not delete the underlying blob.
     *
     * @param artifactId the artifact identifier
     * @param tenantId   the tenant scope
     * @return promise completing when the record is deleted; resolves to {@code true} if deleted,
     *         {@code false} if the record was not found
     */
    Promise<Boolean> delete(String artifactId, String tenantId);

    /**
     * Updates the processing state of a media artifact.
     *
     * @param artifactId      the artifact identifier
     * @param tenantId        the tenant scope
     * @param processingState the new processing state (PENDING, PROCESSING, COMPLETED, FAILED)
     * @return promise completing when the record is updated; resolves to {@code true} if updated,
     *         {@code false} if the record was not found
     */
    Promise<Boolean> updateProcessingState(String artifactId, String tenantId, String processingState);

    /**
     * Updates the lifecycle status of a media artifact.
     *
     * @param artifactId the artifact identifier
     * @param tenantId   the tenant scope
     * @param status     the new lifecycle status (ACTIVE, ARCHIVED, DELETED, EXPIRED)
     * @return promise completing when the record is updated; resolves to {@code true} if updated,
     *         {@code false} if the record was not found
     */
    Promise<Boolean> updateStatus(String artifactId, String tenantId, String status);

    /**
     * Lists all media artifacts with a specific processing state within a tenant.
     *
     * @param processingState the processing state filter
     * @param tenantId        the tenant scope
     * @param limit           maximum number of results
     * @return promise of matching records
     */
    Promise<List<MediaArtifactRecord>> findByProcessingState(String processingState, String tenantId, int limit);

    /**
     * Lists all media artifacts with a specific lifecycle status within a tenant.
     *
     * @param status   the lifecycle status filter
     * @param tenantId the tenant scope
     * @param limit    maximum number of results
     * @return promise of matching records
     */
    Promise<List<MediaArtifactRecord>> findByStatus(String status, String tenantId, int limit);

    /**
     * Lists all processing jobs associated with a media artifact within a tenant.
     *
     * @param artifactId the artifact identifier
     * @param tenantId   the tenant scope
     * @param limit      maximum number of results
     * @return promise of job IDs for processing jobs associated with this artifact
     */
    Promise<List<String>> findProcessingJobsByArtifact(String artifactId, String tenantId, int limit);

    /**
     * Lists all processing results associated with a media artifact within a tenant.
     *
     * @param artifactId the artifact identifier
     * @param tenantId   the tenant scope
     * @param limit      maximum number of results
     * @return promise of result IDs for processing results associated with this artifact
     */
    Promise<List<String>> findProcessingResultsByArtifact(String artifactId, String tenantId, int limit);

    // ==================== Pass 6 - Lifecycle Methods ====================

    /**
     * Finds all processing jobs for a media artifact (P6).
     *
     * @param artifactId the artifact identifier
     * @param tenantId   the tenant scope
     * @return promise of job records
     */
    Promise<List<MediaProcessingJob>> findJobs(String artifactId, String tenantId);

    /**
     * Saves a transcript for a media artifact (P6).
     *
     * @param artifactId the artifact identifier
     * @param tenantId   the tenant scope
     * @param transcript the transcript to save
     * @return promise of the saved transcript
     */
    Promise<Transcript> saveTranscript(String artifactId, String tenantId, Transcript transcript);

    /**
     * Finds the transcript for a media artifact (P6).
     *
     * @param artifactId the artifact identifier
     * @param tenantId   the tenant scope
     * @return promise of the transcript, empty if not found
     */
    Promise<Optional<Transcript>> findTranscript(String artifactId, String tenantId);

    /**
     * Saves a frame index for a media artifact (P6).
     *
     * @param artifactId the artifact identifier
     * @param tenantId   the tenant scope
     * @param frameIndex the frame index to save
     * @return promise of the saved frame index
     */
    Promise<FrameIndex> saveFrameIndex(String artifactId, String tenantId, FrameIndex frameIndex);

    /**
     * Finds the frame index for a media artifact (P6).
     *
     * @param artifactId the artifact identifier
     * @param tenantId   the tenant scope
     * @return promise of the frame index, empty if not found
     */
    Promise<Optional<FrameIndex>> findFrameIndex(String artifactId, String tenantId);

    /**
     * Marks a media artifact as deleted (soft delete) (P6).
     *
     * @param artifactId the artifact identifier
     * @param tenantId   the tenant scope
     * @param deletedBy  user ID who performed the deletion
     * @return promise completing when the record is marked deleted; resolves to {@code true} if marked,
     *         {@code false} if the record was not found
     */
    Promise<Boolean> markDeleted(String artifactId, String tenantId, String deletedBy);

    /**
     * Finds all media artifacts with retention due within a time window (P6).
     *
     * @param tenantId the tenant scope
     * @param limit    maximum number of results
     * @return promise of matching records
     */
    Promise<List<MediaArtifactRecord>> findByRetentionDue(String tenantId, int limit);

    /**
     * Finds all media artifacts with a specific consent status (P6).
     *
     * @param consentStatus the consent status filter (GRANTED, PENDING, DENIED, NOT_REQUIRED)
     * @param tenantId      the tenant scope
     * @param limit         maximum number of results
     * @return promise of matching records
     */
    Promise<List<MediaArtifactRecord>> findByConsentStatus(String consentStatus, String tenantId, int limit);

    /**
     * Updates the consent status of a media artifact (P6).
     *
     * @param artifactId    the artifact identifier
     * @param tenantId      the tenant scope
     * @param consentStatus the new consent status
     * @param updatedBy     user ID who performed the update
     * @return promise completing when the record is updated; resolves to {@code true} if updated,
     *         {@code false} if the record was not found
     */
    Promise<Boolean> updateConsentStatus(String artifactId, String tenantId, String consentStatus, String updatedBy);

    /**
     * Updates the processing job ID for a media artifact (P6).
     *
     * @param artifactId      the artifact identifier
     * @param tenantId        the tenant scope
     * @param processingJobId the processing job ID
     * @param updatedBy       user ID who performed the update
     * @return promise completing when the record is updated; resolves to {@code true} if updated,
     *         {@code false} if the record was not found
     */
    Promise<Boolean> updateProcessingJobId(String artifactId, String tenantId, String processingJobId, String updatedBy);

    /**
     * Updates the transcript ID for a media artifact (P6).
     *
     * @param artifactId   the artifact identifier
     * @param tenantId     the tenant scope
     * @param transcriptId the transcript ID
     * @param updatedBy    user ID who performed the update
     * @return promise completing when the record is updated; resolves to {@code true} if updated,
     *         {@code false} if the record was not found
     */
    Promise<Boolean> updateTranscriptId(String artifactId, String tenantId, String transcriptId, String updatedBy);

    /**
     * Updates the frame index ID for a media artifact (P6).
     *
     * @param artifactId   the artifact identifier
     * @param tenantId     the tenant scope
     * @param frameIndexId the frame index ID
     * @param updatedBy    user ID who performed the update
     * @return promise completing when the record is updated; resolves to {@code true} if updated,
     *         {@code false} if the record was not found
     */
    Promise<Boolean> updateFrameIndexId(String artifactId, String tenantId, String frameIndexId, String updatedBy);

    /**
     * Updates the last error message for a media artifact (P6).
     *
     * @param artifactId the artifact identifier
     * @param tenantId   the tenant scope
     * @param lastError  the last error message
     * @param updatedBy  user ID who performed the update
     * @return promise completing when the record is updated; resolves to {@code true} if updated,
     *         {@code false} if the record was not found
     */
    Promise<Boolean> updateLastError(String artifactId, String tenantId, String lastError, String updatedBy);

    /**
     * Updates the classification for a media artifact (WS3).
     *
     * @param artifactId    the artifact identifier
     * @param tenantId      the tenant scope
     * @param classification the new classification level
     * @param updatedBy     user ID who performed the update
     * @return promise completing when the record is updated; resolves to {@code true} if updated,
     *         {@code false} if the record was not found
     */
    Promise<Boolean> updateClassification(String artifactId, String tenantId, String classification, String updatedBy);

    /**
     * Updates the redaction policy for a media artifact (WS3).
     *
     * @param artifactId      the artifact identifier
     * @param tenantId        the tenant scope
     * @param redactionPolicy the new redaction policy identifier
     * @param updatedBy       user ID who performed the update
     * @return promise completing when the record is updated; resolves to {@code true} if updated,
     *         {@code false} if the record was not found
     */
    Promise<Boolean> updateRedactionPolicy(String artifactId, String tenantId, String redactionPolicy, String updatedBy);

    /**
     * Updates the expiration date for a media artifact (WS3).
     *
     * @param artifactId the artifact identifier
     * @param tenantId   the tenant scope
     * @param expiresAt  the new expiration date
     * @param updatedBy  user ID who performed the update
     * @return promise completing when the record is updated; resolves to {@code true} if updated,
     *         {@code false} if the record was not found
     */
    Promise<Boolean> updateExpiresAt(String artifactId, String tenantId, java.time.Instant expiresAt, String updatedBy);

    /**
     * Updates the owner for a media artifact (WS3).
     *
     * @param artifactId the artifact identifier
     * @param tenantId   the tenant scope
     * @param ownerId    the new owner identifier
     * @param updatedBy  user ID who performed the update
     * @return promise completing when the record is updated; resolves to {@code true} if updated,
     *         {@code false} if the record was not found
     */
    Promise<Boolean> updateOwnerId(String artifactId, String tenantId, String ownerId, String updatedBy);

    /**
     * Updates the source system for a media artifact (WS3).
     *
     * @param artifactId   the artifact identifier
     * @param tenantId     the tenant scope
     * @param sourceSystem the new source system identifier
     * @param updatedBy    user ID who performed the update
     * @return promise completing when the record is updated; resolves to {@code true} if updated,
     *         {@code false} if the record was not found
     */
    Promise<Boolean> updateSourceSystem(String artifactId, String tenantId, String sourceSystem, String updatedBy);

    // ==================== WS3-6: Atomic Job Methods ====================

    /**
     * Creates a new media processing job atomically (WS3-6).
     *
     * @param job the processing job to create
     * @return promise of the created job
     */
    Promise<MediaProcessingJob> createJob(MediaProcessingJob job);

    /**
     * Transitions a job to a new state atomically (WS3-6).
     *
     * @param jobId the job identifier
     * @param tenantId the tenant scope
     * @param newState the new job state
     * @param updatedBy user ID who performed the transition
     * @return promise completing when the job is transitioned; resolves to {@code true} if transitioned,
     *         {@code false} if the job was not found
     */
    Promise<Boolean> transitionJobState(String jobId, String tenantId, String newState, String updatedBy);

    /**
     * Attaches a transcript to a media artifact atomically (WS3-6).
     *
     * @param artifactId the artifact identifier
     * @param tenantId the tenant scope
     * @param transcriptId the transcript identifier
     * @param updatedBy user ID who performed the attachment
     * @return promise completing when the transcript is attached; resolves to {@code true} if attached,
     *         {@code false} if the artifact was not found
     */
    Promise<Boolean> attachTranscript(String artifactId, String tenantId, String transcriptId, String updatedBy);

    /**
     * Attaches a frame index to a media artifact atomically (WS3-6).
     *
     * @param artifactId the artifact identifier
     * @param tenantId the tenant scope
     * @param frameIndexId the frame index identifier
     * @param updatedBy user ID who performed the attachment
     * @return promise completing when the frame index is attached; resolves to {@code true} if attached,
     *         {@code false} if the artifact was not found
     */
    Promise<Boolean> attachFrameIndex(String artifactId, String tenantId, String frameIndexId, String updatedBy);

    /**
     * Marks a job as failed atomically (WS3-6).
     *
     * @param jobId the job identifier
     * @param tenantId the tenant scope
     * @param failureCode the failure code
     * @param failureReason the failure reason
     * @param updatedBy user ID who marked the job as failed
     * @return promise completing when the job is marked failed; resolves to {@code true} if marked,
     *         {@code false} if the job was not found
     */
    Promise<Boolean> markFailed(String jobId, String tenantId, String failureCode, String failureReason, String updatedBy);

    /**
     * Marks a job as cancelled atomically (WS3-6).
     *
     * @param jobId the job identifier
     * @param tenantId the tenant scope
     * @param cancelledBy user ID who cancelled the job
     * @return promise completing when the job is marked cancelled; resolves to {@code true} if marked,
     *         {@code false} if the job was not found
     */
    Promise<Boolean> markCancelled(String jobId, String tenantId, String cancelledBy);

    /**
     * Marks a media artifact as retention expired atomically (WS3-6).
     *
     * @param artifactId the artifact identifier
     * @param tenantId the tenant scope
     * @param updatedBy user ID who marked the artifact as expired
     * @return promise completing when the artifact is marked expired; resolves to {@code true} if marked,
     *         {@code false} if the artifact was not found
     */
    Promise<Boolean> markRetentionExpired(String artifactId, String tenantId, String updatedBy);

    /**
     * Finds jobs by state atomically (WS3-6).
     *
     * @param state the job state filter
     * @param tenantId the tenant scope
     * @param limit maximum number of results
     * @return promise of matching jobs
     */
    Promise<List<MediaProcessingJob>> findJobsByState(String state, String tenantId, int limit);
}
