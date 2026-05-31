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
}
