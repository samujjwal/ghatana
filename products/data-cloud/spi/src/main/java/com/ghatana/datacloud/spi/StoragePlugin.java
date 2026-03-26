package com.ghatana.datacloud.spi;

import com.ghatana.datacloud.*;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Unified Storage Plugin SPI for all record types.
 *
 * <p>
 * <b>Purpose</b><br>
 * This is the core Service Provider Interface that ALL storage backends must
 * implement. It provides a unified abstraction for storing and querying records
 * regardless of:
 * <ul>
 * <li>Record type (Entity, Event, TimeSeries, Graph, Document)</li>
 * <li>Storage backend (PostgreSQL, Cassandra, MongoDB, Redis, etc.)</li>
 * <li>Deployment model (local, distributed, cloud)</li>
 * </ul>
 *
 * <p>
 * <b>Design Principles</b><br>
 * <ul>
 * <li>Fully async using ActiveJ Promise</li>
 * <li>Multi-tenant by design (tenantId in all operations)</li>
 * <li>Type-safe with generic DataRecord type</li>
 * <li>Supports all CRUD operations + queries</li>
 * <li>Optional capabilities (streaming, aggregation, etc.)</li>
 * </ul>
 *
 * <p>
 * <b>Implementation Notes</b><br>
 * Implementations should:
 * <ul>
 * <li>Register via {@link StoragePluginRegistry}</li>
 * <li>Handle connection pooling internally</li>
 * <li>Implement proper error handling with meaningful exceptions</li>
 * <li>Support idempotent writes where possible</li>
 * <li>Respect record type constraints (e.g., immutability for EVENT)</li>
 * </ul>
 *
 * @param <R> The record type this plugin handles (must extend Record)
 * @see DataRecord
 * @see DataRecordQuery
 * @see StoragePluginRegistry
 * @doc.type interface
 * @doc.purpose Core storage abstraction
 * @doc.layer core
 * @doc.pattern Service Provider Interface
 */
public interface StoragePlugin<R extends DataRecord> {

    // ==================== Plugin Identity ====================
    /**
     * Returns the unique identifier for this plugin.
     *
     * @return Plugin ID (e.g., "postgresql", "cassandra", "mongodb")
     */
    String getPluginId();

    /**
     * Returns a human-readable name for this plugin.
     *
     * @return Display name
     */
    String getDisplayName();

    /**
     * Returns the version of this plugin.
     *
     * @return Semantic version string
     */
    String getVersion();

    /**
     * Returns the record types this plugin supports.
     *
     * @return List of supported record types
     */
    List<RecordType> getSupportedRecordTypes();

    /**
     * Checks if this plugin supports a specific record type.
     *
     * @param recordType The record type to check
     * @return true if supported
     */
    default boolean supportsRecordType(RecordType recordType) {
        return getSupportedRecordTypes().contains(recordType);
    }

    // ==================== Lifecycle ====================
    /**
     * Initializes the plugin with configuration.
     *
     * @param config Plugin-specific configuration
     * @return Promise completing when initialization is done
     */
    Promise<Void> initialize(Map<String, Object> config);

    /**
     * Shuts down the plugin, releasing resources.
     *
     * @return Promise completing when shutdown is done
     */
    Promise<Void> shutdown();

    /**
     * Checks if the plugin is healthy and ready to serve requests.
     *
     * @return Promise with health status
     */
    Promise<HealthStatus> healthCheck();

    // ==================== Collection Management ====================
    /**
     * Creates a new collection.
     *
     * @param collection Collection definition
     * @return Promise with created collection
     */
    Promise<Collection> createCollection(Collection collection);

    /**
     * Gets a collection by name.
     *
     * @param tenantId Tenant ID
     * @param name Collection name
     * @return Promise with collection if found
     */
    Promise<Optional<Collection>> getCollection(String tenantId, String name);

    /**
     * Updates a collection definition.
     *
     * @param collection Updated collection
     * @return Promise with updated collection
     */
    Promise<Collection> updateCollection(Collection collection);

    /**
     * Deletes a collection.
     *
     * @param tenantId Tenant ID
     * @param name Collection name
     * @return Promise completing when deleted
     */
    Promise<Void> deleteCollection(String tenantId, String name);

    /**
     * Lists all collections for a tenant.
     *
     * @param tenantId Tenant ID
     * @return Promise with list of collections
     */
    Promise<List<Collection>> listCollections(String tenantId);

    // ==================== CRUD Operations ====================
    /**
     * Inserts a single record.
     *
     * @param record Record to insert
     * @return Promise with inserted record (with generated ID)
     */
    Promise<R> insert(R record);

    /**
     * Inserts multiple records in batch.
     *
     * @param records Records to insert
     * @return Promise with insert result
     */
    Promise<BatchResult> insertBatch(List<R> records);

    /**
     * Gets a record by ID.
     *
     * @param tenantId Tenant ID
     * @param collectionName Collection name
     * @param id Record ID
     * @return Promise with record if found
     */
    Promise<Optional<R>> getById(String tenantId, String collectionName, UUID id);

    /**
     * Gets multiple records by IDs.
     *
     * @param tenantId Tenant ID
     * @param collectionName Collection name
     * @param ids Record IDs
     * @return Promise with found records
     */
    Promise<List<R>> getByIds(String tenantId, String collectionName, List<UUID> ids);

    /**
     * Updates a record (only for mutable record types).
     *
     * @param record Updated record
     * @return Promise with updated record
     * @throws UnsupportedOperationException if record type is immutable
     */
    Promise<R> update(R record);

    /**
     * Updates multiple records in batch.
     *
     * @param records Records to update
     * @return Promise with update result
     */
    Promise<BatchResult> updateBatch(List<R> records);

    /**
     * Deletes a record by ID (soft delete for ENTITY, hard delete for others).
     *
     * @param tenantId Tenant ID
     * @param collectionName Collection name
     * @param id Record ID
     * @return Promise completing when deleted
     */
    Promise<Void> delete(String tenantId, String collectionName, UUID id);

    /**
     * Deletes multiple records.
     *
     * @param tenantId Tenant ID
     * @param collectionName Collection name
     * @param ids Record IDs
     * @return Promise with delete result
     */
    Promise<BatchResult> deleteBatch(String tenantId, String collectionName, List<UUID> ids);

    // ==================== Query Operations ====================
    /**
     * Executes a query and returns matching records.
     *
     * @param query Query specification
     * @return Promise with query result
     */
    Promise<QueryResult<R>> query(RecordQuery query);

    /**
     * Counts records matching a query.
     *
     * @param query Query specification
     * @return Promise with count
     */
    Promise<Long> count(RecordQuery query);

    /**
     * Checks if any records match the query.
     *
     * @param query Query specification
     * @return Promise with existence check
     */
    Promise<Boolean> exists(RecordQuery query);

    // ==================== Supporting Types ====================
    /**
     * Health status of the plugin.
     */
    record HealthStatus(
            boolean isHealthy,
            String message,
            Map<String, Object> details
            ) {

        public static HealthStatus ok() {
            return new HealthStatus(true, "OK", Map.of());
        }

        public static HealthStatus ok(Map<String, Object> details) {
            return new HealthStatus(true, "OK", details);
        }

        public static HealthStatus error(String message) {
            return new HealthStatus(false, message, Map.of());
        }

        public static HealthStatus error(String message, Map<String, Object> details) {
            return new HealthStatus(false, message, details);
        }
    }

    /**
     * Result of a batch operation.
     */
    record BatchResult(
            int totalCount,
            int successCount,
            int failureCount,
            List<BatchError> errors
            ) {

        public boolean isFullySuccessful() {
            return failureCount == 0;
        }

        public boolean isPartiallySuccessful() {
            return successCount > 0 && failureCount > 0;
        }

        public static BatchResult success(int count) {
            return new BatchResult(count, count, 0, List.of());
        }

        public static BatchResult failure(int count, List<BatchError> errors) {
            return new BatchResult(count, 0, count, errors);
        }
    }

    /**
     * Error detail for batch operations.
     */
    record BatchError(
            int index,
            UUID recordId,
            String errorCode,
            String errorMessage
            ) {

    }

    /**
     * Result of a query operation.
     */
    record QueryResult<R>(
            List<R> records,
            long totalCount,
            boolean hasMore,
            String continuationToken
            ) {

        public static <R> QueryResult<R> empty() {
            return new QueryResult<>(List.of(), 0, false, null);
        }

        public static <R> QueryResult<R> of(List<R> records) {
            return new QueryResult<>(records, records.size(), false, null);
        }

        public static <R> QueryResult<R> of(List<R> records, long totalCount) {
            return new QueryResult<>(records, totalCount, records.size() < totalCount, null);
        }

        public static <R> QueryResult<R> withContinuation(List<R> records, long totalCount, String token) {
            return new QueryResult<>(records, totalCount, token != null, token);
        }
    }
}
