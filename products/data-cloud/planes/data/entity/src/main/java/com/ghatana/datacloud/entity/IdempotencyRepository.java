package com.ghatana.datacloud.entity;

import io.activej.promise.Promise;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for idempotency record data access.
 *
 * <p><b>Purpose</b><br>
 * Defines the contract for idempotency record persistence operations.
 * Used to track idempotency keys for retry-safe entity operations.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * IdempotencyRepository repo = ...;
 *
 * // Check if idempotency key exists
 * Promise<Optional<IdempotencyRecord>> existing = repo.findByKey(
 *     "tenant-123", "orders", "req-abc-123"
 * );
 *
 * // Save new idempotency record
 * IdempotencyRecord record = IdempotencyRecord.builder()
 *     .tenantId("tenant-123")
 *     .collectionName("orders")
 *     .idempotencyKey("req-abc-123")
 *     .entityId(entityId)
 *     .build();
 * repo.save(record);
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Implementations must be thread-safe.
 *
 * @see IdempotencyRecord
 * @doc.type interface
 * @doc.purpose Repository port for idempotency persistence
 * @doc.layer product
 * @doc.pattern Repository Port (Domain Layer)
 */
public interface IdempotencyRepository {

    /**
     * Finds an idempotency record by key within a tenant and collection.
     *
     * <p><b>Multi-Tenancy</b><br>
     * Only returns record if it belongs to the specified tenant and collection.
     *
     * @param tenantId the tenant identifier (required)
     * @param collectionName the collection name (required)
     * @param idempotencyKey the idempotency key (required)
     * @return Promise of Optional containing the record if found and not expired
     */
    Promise<Optional<IdempotencyRecord>> findByKey(String tenantId, String collectionName, String idempotencyKey);

    /**
     * Saves an idempotency record.
     *
     * @param record the record to save (required)
     * @return Promise of saved record
     */
    Promise<IdempotencyRecord> save(IdempotencyRecord record);

    /**
     * Deletes expired idempotency records.
     *
     * <p><b>Cleanup</b><br>
     * Should be called periodically to prevent unbounded growth.
     *
     * @return Promise of number of records deleted
     */
    Promise<Integer> deleteExpired();

    /**
     * Deletes an idempotency record by ID.
     *
     * @param id the record ID (required)
     * @return Promise of void
     */
    Promise<Void> deleteById(UUID id);
}
