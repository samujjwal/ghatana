/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.memory.media;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * SPI for persisting and retrieving {@link TranscriptRecord} instances.
 *
 * <p>Implementations must enforce tenant isolation: all operations are scoped
 * to the provided {@code tenantId} and must not expose records across tenants.
 *
 * @doc.type interface
 * @doc.purpose SPI for transcript persistence with lineage
 * @doc.layer product
 * @doc.pattern Repository
 */
public interface TranscriptRepository {

    /**
     * Persists a new transcript record.
     *
     * @param record the record to save; must not be null
     * @return promise of the persisted record
     */
    Promise<TranscriptRecord> save(TranscriptRecord record);

    /**
     * Finds a transcript by its unique transcript ID, scoped to the given tenant.
     *
     * @param transcriptId the transcript identifier
     * @param tenantId     the tenant scope
     * @return promise of the record, empty if not found
     */
    Promise<Optional<TranscriptRecord>> findById(String transcriptId, String tenantId);

    /**
     * Lists all transcripts for a specific source artifact within a tenant.
     *
     * @param sourceArtifactId the source media artifact ID
     * @param tenantId         the tenant scope
     * @param limit            maximum number of results
     * @return promise of matching records
     */
    Promise<List<TranscriptRecord>> findBySourceArtifact(String sourceArtifactId, String tenantId, int limit);

    /**
     * Lists all transcripts produced by a specific agent within a tenant.
     *
     * @param agentId  the agent identifier
     * @param tenantId the tenant scope
     * @param limit    maximum number of results
     * @return promise of matching records
     */
    Promise<List<TranscriptRecord>> findByAgent(String agentId, String tenantId, int limit);

    /**
     * Deletes a transcript record.
     *
     * @param transcriptId the transcript identifier
     * @param tenantId     the tenant scope
     * @return promise completing when the record is deleted; resolves to {@code true} if deleted,
     *         {@code false} if the record was not found
     */
    Promise<Boolean> delete(String transcriptId, String tenantId);
}
