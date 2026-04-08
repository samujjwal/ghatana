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
}
