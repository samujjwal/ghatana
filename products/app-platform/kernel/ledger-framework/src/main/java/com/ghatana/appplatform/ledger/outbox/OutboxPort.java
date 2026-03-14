/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.outbox;

import io.activej.promise.Promise;

import java.util.List;
import java.util.UUID;

/**
 * Storage port for transactional outbox operations (K17-001/002/003).
 *
 * <p>The fetch operation MUST use {@code SELECT FOR UPDATE SKIP LOCKED} so that
 * multiple relay instances can run concurrently without double-delivering messages.
 *
 * @doc.type interface
 * @doc.purpose Storage port for transactional outbox pattern (K17-001)
 * @doc.layer core
 * @doc.pattern Repository
 */
public interface OutboxPort {

    /**
     * Fetches a batch of unpublished outbox entries and locks them for processing.
     *
     * <p>Uses {@code SELECT FOR UPDATE SKIP LOCKED} to prevent concurrent relay
     * instances from processing the same batch.
     *
     * @param batchSize maximum number of entries to fetch
     * @return locked batch of unpublished entries eligible for delivery
     */
    Promise<List<OutboxEntry>> fetchUnpublished(int batchSize);

    /**
     * Marks a batch of entries as successfully published.
     *
     * @param ids IDs of entries to mark published
     * @return completion promise
     */
    Promise<Void> markPublished(List<UUID> ids);

    /**
     * Increments the attempt counter and records the error for a failed entry.
     *
     * @param id    outbox entry ID
     * @param error error message or exception summary
     * @return completion promise
     */
    Promise<Void> markFailed(UUID id, String error);

    /**
     * Deletes published entries older than the given number of days (cleanup).
     *
     * @param retentionDays entries older than this will be deleted
     * @return number of rows deleted
     */
    Promise<Integer> cleanupOlderThan(int retentionDays);
}
