package com.ghatana.datacloud.spi;

import com.ghatana.datacloud.entity.EntityInterface;
import com.ghatana.datacloud.entity.storage.QuerySpecInterface;
import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Data Storage Plugin interface for traditional CRUD operations.
 * 
 * <p>This interface represents storage plugins that handle structured data
 * with full CRUD capabilities, preprocessing, and postprocessing. This is
 * distinct from event sourcing plugins which focus on append-only event logs.
 * 
 * <p><b>Two Types of Storage Plugins:</b>
 * <ol>
 *   <li><b>DataStoragePlugin</b> (this interface) - CRUD operations for structured data
 *       <ul>
 *         <li>Create, Read, Update, Delete entities</li>
 *         <li>Query with filters and pagination</li>
 *         <li>Preprocessing (validation, transformation before storage)</li>
 *         <li>Postprocessing (enrichment, formatting after retrieval)</li>
 *         <li>Examples: PostgreSQL, MongoDB, Elasticsearch, S3</li>
 *       </ul>
 *   </li>
 *   <li><b>StoragePlugin</b> (existing) - Event sourcing operations
 *       <ul>
 *         <li>Append-only event log</li>
 *         <li>Event query and replay</li>
 *         <li>Examples: Kafka, EventStore, custom event logs</li>
 *       </ul>
 *   </li>
 * </ol>
 * 
 * <p><b>Uniform Storage Operations:</b><br>
 * Both plugin types provide uniform interfaces regardless of underlying
 * storage mechanism (database, file system, remote storage, etc.). This
 * allows applications to work with different storage backends without
 * changing business logic.
 * 
 * <p><b>Example Usage:</b>
 * <pre>{@code
 * // PostgreSQL implementation
 * DataStoragePlugin postgresPlugin = new PostgresDataStoragePlugin(config);
 * 
 * // S3 implementation
 * DataStoragePlugin s3Plugin = new S3DataStoragePlugin(config);
 * 
 * // Both expose same operations
 * Promise<EntityInterface> entity = plugin.create(tenantId, collection, data);
 * }</pre>
 * 
 * <p><b>Preprocessing/Postprocessing:</b>
 * <pre>{@code
 * // Before storage (preprocessing)
 * - Validate data against schema
 * - Transform formats (e.g., dates, numbers)
 * - Encrypt sensitive fields
 * - Generate IDs and timestamps
 * 
 * // After retrieval (postprocessing)
 * - Decrypt fields
 * - Join with related data
 * - Apply computed fields
 * - Format for client
 * }</pre>
 * 
 * @doc.type interface
 * @doc.purpose CRUD storage plugin for structured data
 * @doc.layer core
 * @doc.pattern Plugin, Strategy
 */
public interface DataStoragePlugin extends Plugin {
    
    /**
     * Creates a new entity in the specified collection.
     * 
     * <p>Preprocessing includes:
     * <ul>
     *   <li>Schema validation</li>
     *   <li>ID generation if not provided</li>
     *   <li>Timestamp setting (createdAt, updatedAt)</li>
     *   <li>Field encryption/hashing</li>
     *   <li>Data transformation</li>
     * </ul>
     * 
     * @param tenantId Tenant identifier for multi-tenancy
     * @param collectionName Collection/table name
     * @param data Entity data (fields and values)
     * @return Promise with created entity (includes generated ID and metadata)
     */
    Promise<EntityInterface> create(String tenantId, String collectionName, Map<String, Object> data);
    
    /**
     * Reads an entity by its ID.
     * 
     * <p>Postprocessing includes:
     * <ul>
     *   <li>Field decryption</li>
     *   <li>Related data loading (if configured)</li>
     *   <li>Computed fields calculation</li>
     *   <li>Format transformation</li>
     * </ul>
     * 
     * @param tenantId Tenant identifier
     * @param collectionName Collection/table name
     * @param entityId Entity unique identifier
     * @return Promise with Optional containing entity if found, empty otherwise
     */
    Promise<Optional<EntityInterface>> read(String tenantId, String collectionName, UUID entityId);
    
    /**
     * Updates an existing entity (partial or full update).
     * 
     * <p>Preprocessing includes:
     * <ul>
     *   <li>Version conflict detection (optimistic locking)</li>
     *   <li>Field validation</li>
     *   <li>Updated timestamp setting</li>
     *   <li>Audit trail creation</li>
     * </ul>
     * 
     * @param tenantId Tenant identifier
     * @param collectionName Collection/table name
     * @param entityId Entity unique identifier
     * @param updates Fields to update (partial update supported)
     * @return Promise with updated entity
     */
    Promise<EntityInterface> update(String tenantId, String collectionName, UUID entityId, Map<String, Object> updates);
    
    /**
     * Deletes an entity (soft or hard delete depending on configuration).
     * 
     * @param tenantId Tenant identifier
     * @param collectionName Collection/table name
     * @param entityId Entity unique identifier
     * @return Promise with void (completes when deleted)
     */
    Promise<Void> delete(String tenantId, String collectionName, UUID entityId);
    
    /**
     * Queries entities with filtering, sorting, and pagination.
     * 
     * <p>Supports:
     * <ul>
     *   <li>Field filters (equals, not equals, greater than, etc.)</li>
     *   <li>Logical operators (AND, OR, NOT)</li>
     *   <li>Sorting (single or multiple fields)</li>
     *   <li>Pagination (offset/limit or cursor-based)</li>
     *   <li>Projection (select specific fields)</li>
     * </ul>
     * 
     * @param tenantId Tenant identifier
     * @param collectionName Collection/table name
     * @param query Query specification (filters, sort, pagination)
     * @return Promise with list of matching entities
     */
    Promise<List<EntityInterface>> query(String tenantId, String collectionName, QuerySpecInterface query);
    
    /**
     * Counts entities matching the query (without retrieving data).
     * 
     * @param tenantId Tenant identifier
     * @param collectionName Collection/table name
     * @param query Query specification (only filters considered)
     * @return Promise with count of matching entities
     */
    Promise<Long> count(String tenantId, String collectionName, QuerySpecInterface query);
    
    /**
     * Checks if the plugin supports batch operations efficiently.
     * 
     * @return true if batch operations are optimized, false otherwise
     */
    default boolean supportsBatchOperations() {
        return false;
    }
    
    /**
     * Creates multiple entities in a single batch operation.
     * Only called if {@link #supportsBatchOperations()} returns true.
     * 
     * @param tenantId Tenant identifier
     * @param collectionName Collection/table name
     * @param entities List of entities to create
     * @return Promise with list of created entities
     */
    default Promise<List<EntityInterface>> batchCreate(String tenantId, String collectionName, List<Map<String, Object>> entities) {
        return Promise.ofException(new UnsupportedOperationException("Batch operations not supported"));
    }
    
    /**
     * Checks if the plugin supports transactions.
     * 
     * @return true if transactions are supported, false otherwise
     */
    default boolean supportsTransactions() {
        return false;
    }
    
    /**
     * Begins a new transaction.
     * Only called if {@link #supportsTransactions()} returns true.
     * 
     * @return Promise with transaction handle
     */
    default Promise<Object> beginTransaction() {
        return Promise.ofException(new UnsupportedOperationException("Transactions not supported"));
    }
    
    /**
     * Commits a transaction.
     * 
     * @param transaction Transaction handle from beginTransaction
     * @return Promise completing when committed
     */
    default Promise<Void> commitTransaction(Object transaction) {
        return Promise.ofException(new UnsupportedOperationException("Transactions not supported"));
    }
    
    /**
     * Rolls back a transaction.
     * 
     * @param transaction Transaction handle from beginTransaction
     * @return Promise completing when rolled back
     */
    default Promise<Void> rollbackTransaction(Object transaction) {
        return Promise.ofException(new UnsupportedOperationException("Transactions not supported"));
    }
    
    /**
     * Returns storage statistics for monitoring and optimization.
     * 
     * @param tenantId Tenant identifier
     * @param collectionName Collection/table name
     * @return Promise with storage statistics
     */
    Promise<StorageStats> getStats(String tenantId, String collectionName);
    
    /**
     * Storage statistics for a collection.
     */
    record StorageStats(
        long totalEntities,
        long storageBytes,
        double avgReadLatencyMs,
        double avgWriteLatencyMs,
        Map<String, Object> customMetrics
    ) {}
}
