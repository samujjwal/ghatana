package com.ghatana.core.database;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Promise-based database client for asynchronous document operations.
 * <p>
 * This interface provides a high-level abstraction for database operations
 * using ActiveJ Promises for non-blocking execution. It is designed to work
 * with document-style data using Map representations for flexibility.
 * </p>
 *
 * <h3>Design Principles:</h3>
 * <ul>
 *   <li><b>Non-blocking</b>: All operations return ActiveJ Promises</li>
 *   <li><b>Document-oriented</b>: Works with Map&lt;String, Object&gt; for flexibility</li>
 *   <li><b>Collection-based</b>: Operations organized by logical collections</li>
 *   <li><b>Tenant-aware</b>: Supports multi-tenancy (filters should include tenantId)</li>
 * </ul>
 *
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * DatabaseClient client = ...;
 * 
 * // Query documents
 * Promise<List<Map<String, Object>>> results = client.query(
 *     "users",
 *     Map.of("tenantId", "tenant-123", "status", "ACTIVE"),
 *     10
 * );
 * 
 * // Insert document
 * Map<String, Object> doc = Map.of(
 *     "tenantId", "tenant-123",
 *     "name", "John Doe",
 *     "email", "john@example.com"
 * );
 * Promise<Void> inserted = client.insert("users", doc);
 * 
 * // Update documents
 * Promise<Void> updated = client.update(
 *     "users",
 *     Map.of("userId", "user-123"),
 *     Map.of("status", "INACTIVE")
 * );
 * }</pre>
 *
 * <h3>Implementation Notes:</h3>
 * <ul>
 *   <li>Implementations should handle connection pooling</li>
 *   <li>Error handling should use Promise.ofException()</li>
 *   <li>Operations should be atomic where possible</li>
 *   <li>Filtering should support common operators (equality, range, etc.)</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Async database client for document operations
 * @doc.layer core
 * @doc.pattern Repository
 * 
 * @see io.activej.promise.Promise
 * @see Repository
 * @since 1.0.0
 */
public interface DatabaseClient {

    /**
     * Query documents from a collection matching the given filter.
     * <p>
     * Returns a Promise that resolves to a list of documents matching the filter criteria.
     * The filter is applied using AND logic for all key-value pairs.
     * </p>
     *
     * @param collection the name of the collection to query (e.g., "users", "requirements_published")
     * @param filter     key-value pairs for filtering (e.g., Map.of("tenantId", "t1", "status", "ACTIVE"))
     * @param limit      maximum number of documents to return (use 0 for no limit)
     * @return Promise resolving to list of matching documents
     * @throws IllegalArgumentException if collection is null or empty
     */
    Promise<List<Map<String, Object>>> query(
            String collection,
            Map<String, Object> filter,
            int limit
    );

    /**
     * Insert a single document into a collection.
     * <p>
     * Returns a Promise that completes when the document has been successfully inserted.
     * If a document with the same primary key exists, behavior is implementation-dependent.
     * </p>
     *
     * @param collection the name of the collection (e.g., "users")
     * @param document   the document to insert (must not be null)
     * @return Promise completing on successful insertion
     * @throws IllegalArgumentException if collection or document is null
     */
    Promise<Void> insert(
            String collection,
            Map<String, Object> document
    );

    /**
     * Update documents in a collection matching the given filter.
     * <p>
     * Returns a Promise that completes when matching documents have been updated.
     * The update map contains field-value pairs to set on matching documents.
     * </p>
     *
     * @param collection the name of the collection (e.g., "users")
     * @param filter     criteria to select documents to update
     * @param update     field-value pairs to update on matching documents
     * @return Promise completing on successful update
     * @throws IllegalArgumentException if any parameter is null
     */
    Promise<Void> update(
            String collection,
            Map<String, Object> filter,
            Map<String, Object> update
    );

    /**
     * Delete documents from a collection matching the given filter.
     * <p>
     * Returns a Promise that completes when matching documents have been deleted.
     * Use with caution - deleted documents cannot be recovered unless backups exist.
     * </p>
     *
     * @param collection the name of the collection (e.g., "users")
     * @param filter     criteria to select documents to delete
     * @return Promise completing on successful deletion
     * @throws IllegalArgumentException if collection or filter is null
     */
    Promise<Void> delete(
            String collection,
            Map<String, Object> filter
    );

    /**
     * Count documents in a collection matching the given filter.
     * <p>
     * Returns a Promise resolving to the count of documents matching the filter.
     * Useful for pagination and analytics.
     * </p>
     *
     * @param collection the name of the collection (e.g., "users")
     * @param filter     criteria to count documents (empty map matches all)
     * @return Promise resolving to document count
     * @throws IllegalArgumentException if collection is null
     */
    default Promise<Long> count(
            String collection,
            Map<String, Object> filter
    ) {
        return query(collection, filter, 0)
                .map(results -> (long) results.size());
    }

    /**
     * Check if any documents exist matching the given filter.
     * <p>
     * More efficient than count() when you only need to know if matches exist.
     * </p>
     *
     * @param collection the name of the collection (e.g., "users")
     * @param filter     criteria to check for existence
     * @return Promise resolving to true if at least one document matches
     * @throws IllegalArgumentException if collection is null
     */
    default Promise<Boolean> exists(
            String collection,
            Map<String, Object> filter
    ) {
        return query(collection, filter, 1)
                .map(results -> !results.isEmpty());
    }

    /**
     * Insert multiple documents in a single batch operation.
     * <p>
     * More efficient than multiple insert() calls when inserting many documents.
     * Behavior on partial failure is implementation-dependent.
     * Default implementation executes inserts sequentially.
     * </p>
     *
     * @param collection the name of the collection (e.g., "users")
     * @param documents  list of documents to insert (must not be null or empty)
     * @return Promise completing on successful batch insertion
     * @throws IllegalArgumentException if collection or documents is null/empty
     */
    default Promise<Void> insertBatch(
            String collection,
            List<Map<String, Object>> documents
    ) {
        if (documents == null || documents.isEmpty()) {
            return Promise.ofException(new IllegalArgumentException("documents must not be null or empty"));
        }

        // Sequential insertion (override for better batch performance)
        Promise<Void> promise = Promise.complete();
        for (Map<String, Object> doc : documents) {
            promise = promise.then($ -> insert(collection, doc));
        }
        return promise;
    }
}
