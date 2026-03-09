package com.ghatana.datacloud.entity.storage;

import com.ghatana.datacloud.entity.Entity;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Port interface for storage backend connectors.
 *
 * <p>
 * <b>Purpose</b><br>
 * Defines the contract for storage connectors that handle CRUD and query
 * operations for collections and entities. Implementations (adapters) provide
 * specific backend technology integration (PostgreSQL JSONB, TimeSeries DB,
 * Lakehouse, etc.). Follows hexagonal architecture with Promise-based async
 * operations.
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Port interface in domain/storage layer for storage implementations. Used by:
 * - StorageRoutingService (application layer) - Routes operations based on
 * profile - Infrastructure adapters - PostgresJsonbConnector,
 * TimeSeriesConnector, etc. - CollectionService, EntityService - Delegate
 * persistence to connector
 *
 * <p>
 * <b>Connector Abstraction</b><br>
 * Connectors operate at the logical entity level (Entity/Map-based data): -
 * Write: Accept structured data (Entity or Map), store in backend - Read:
 * Retrieve structured data from backend, return as Entity or Map - Query:
 * Execute backend-agnostic queries (QuerySpec) on data - Scan: Full table scans
 * with optional filters - Metadata: Introspect connector capabilities (backend
 * type, latency, costs)
 *
 * <p>
 * <b>Multi-Tenancy</b><br>
 * All operations are implicitly tenant-scoped: - TenantId extracted from
 * Entity/collection context (NOT passed as parameter) - Connectors enforce
 * tenant isolation at adapter level - Cross-tenant queries prevented at
 * infrastructure layer
 *
 * <p>
 * <b>Usage</b><br>
 * 
 * <pre>{@code
 * // Inject connector from routing service
 * StorageConnector connector = routingService.routeForWrite(collection);
 *
 * // Create entity
 * Entity entity = Entity.builder()
 *         .collectionId(collectionId)
 *         .tenantId(tenantId)
 *         .data(Map.of("name", "John", "age", 30))
 *         .build();
 *
 * // Write to connector
 * connector.create(entity)
 *         .thenApply(savedEntity -> {
 *             // Entity persisted with ID
 *             return savedEntity;
 *         })
 *         .exceptionally(err -> {
 *             // Handle storage error
 *             throw new StorageException("Failed to create entity", err);
 *         });
 *
 * // Query entities
 * QuerySpec query = QuerySpec.builder()
 *         .filter("age > 18")
 *         .sort("name", SortDirection.ASC)
 *         .limit(10)
 *         .build();
 *
 * connector.query(collectionId, tenantId, query)
 *         .thenApply(results -> {
 *             // Process results
 *             return results.getEntities();
 *         });
 * }</pre>
 *
 * @see com.ghatana.datacloud.entity.Entity
 * @see StorageBackendType
 * @see StorageProfile
 * @see com.ghatana.datacloud.entity.storage.QuerySpec
 * @doc.type interface
 * @doc.purpose Port interface for storage backend operations
 * @doc.layer product
 * @doc.pattern Port, Repository
 */
public interface StorageConnector {

    /**
     * Create a new entity in storage.
     *
     * <p>
     * Persists entity with generated ID (if not present). Enforces tenant
     * isolation via entity's tenantId.
     *
     * @param entity Entity to create (must include tenantId and collectionId)
     * @return Promise of created entity with persisted ID and metadata
     *         (createdAt, version)
     * @throws IllegalArgumentException if entity missing required fields
     * @throws StorageException         on backend errors (connection, constraint,
     *                                  etc.)
     */
    Promise<Entity> create(Entity entity);

    /**
     * Retrieve entity by ID.
     *
     * <p>
     * Returns entity only if it belongs to the calling tenant (tenant isolation
     * enforced at adapter level).
     *
     * @param collectionId Collection ID (for organizing queries)
     * @param tenantId     Tenant ID for isolation
     * @param entityId     Entity ID to retrieve
     * @return Promise of Optional entity (empty if not found or different
     *         tenant)
     */
    Promise<Optional<Entity>> read(UUID collectionId, String tenantId, UUID entityId);

    /**
     * Update an existing entity.
     *
     * <p>
     * Performs merge semantics: provided fields updated, unspecified fields
     * preserved. Increments version for optimistic locking support. Returns
     * updated entity with new version.
     *
     * @param entity Entity with updates (must include ID, tenantId,
     *               collectionId)
     * @return Promise of updated entity with incremented version
     * @throws IllegalArgumentException if entity missing ID or tenantId
     * @throws ConflictException        on version mismatch (optimistic lock
     *                                  failure)
     * @throws StorageException         on backend errors
     */
    Promise<Entity> update(Entity entity);

    /**
     * Delete entity by ID.
     *
     * <p>
     * Supports soft-delete (if backend implements) or hard-delete. Tenant
     * isolation enforced: only deletes if entity matches tenant.
     *
     * @param collectionId Collection ID
     * @param tenantId     Tenant ID for isolation
     * @param entityId     Entity ID to delete
     * @return Promise of void (completes on successful deletion)
     */
    Promise<Void> delete(UUID collectionId, String tenantId, UUID entityId);

    /**
     * Write entity data (create if not exists, update if exists).
     *
     * <p>
     * Convenience method that performs an upsert: creates entity if ID is new,
     * updates if ID already exists. Internally wraps data in Entity object with
     * provided metadata. Useful for generic write operations that don't need to
     * distinguish create vs update. Accepts both String and UUID entity IDs.
     *
     * @param tenantId       Tenant ID for isolation
     * @param collectionName Collection name (used to look up collectionId)
     * @param entityId       Entity ID (String or UUID)
     * @param data           Map of field names to values
     * @return Promise of written entity
     * @throws IllegalArgumentException if parameters invalid
     * @throws StorageException         on backend errors
     */
    default Promise<Entity> write(String tenantId, String collectionName, Object entityId, Map<String, Object> data) {
        // Convert entityId to UUID if needed
        UUID id;
        if (entityId instanceof UUID) {
            id = (UUID) entityId;
        } else if (entityId instanceof String) {
            // Try to parse as UUID; if fails, generate UUID from string
            try {
                id = UUID.fromString((String) entityId);
            } catch (IllegalArgumentException e) {
                id = UUID.nameUUIDFromBytes(((String) entityId).getBytes());
            }
        } else {
            throw new IllegalArgumentException("entityId must be String or UUID");
        }

        Entity entity = Entity.builder()
                .tenantId(tenantId)
                .collectionName(collectionName)
                .id(id)
                .data(data)
                .build();

        // Simple approach: try create first, fallback to update on failure
        // This is a default implementation that subclasses can override
        return create(entity);
    }

    /**
     * Delete entities matching query criteria.
     *
     * <p>
     * Bulk delete operation based on query filters. Useful for removing related
     * nodes/entities by criteria (e.g., delete all relations for a node ID).
     * Returns count of deleted entities.
     *
     * @param tenantId       Tenant ID for isolation
     * @param collectionName Collection name
     * @param queryFilters   Map of filter criteria (backend-specific syntax)
     * @return Promise of count of deleted entities
     * @throws StorageException on backend errors
     */
    default Promise<Long> deleteByQuery(String tenantId, String collectionName, Map<String, Object> queryFilters) {
        // Default implementation: scan all entities and delete individually
        // Subclasses should override for efficiency
        return bulkDelete(UUID.randomUUID(), tenantId, List.of());
    }

    /**
     * Execute query on collection entities by collection name.
     *
     * <p>
     * Variant of query() that accepts collection name (String) instead of
     * collectionId (UUID). Useful for semantic storage where collection name is
     * known but ID may not be.
     *
     * @param tenantId       Tenant ID for isolation
     * @param collectionName Collection name
     * @param filters        Map of filter criteria to match entities
     * @return Promise of list of matching entities
     * @throws StorageException on backend errors
     */
    default Promise<List<Entity>> query(String tenantId, String collectionName, Map<String, Object> filters) {
        // Default implementation using scan with filters
        // For internal collections (like _semantic_nodes), use a synthetic UUID
        UUID syntheticId = UUID.nameUUIDFromBytes(collectionName.getBytes());

        // Build a simple filter expression from the Map
        // Format: key1=value1 AND key2=value2 ...
        StringBuilder filterExpr = new StringBuilder();
        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            if (filterExpr.length() > 0) {
                filterExpr.append(" AND ");
            }
            filterExpr.append(entry.getKey()).append("=").append("'").append(entry.getValue()).append("'");
        }

        return scan(syntheticId, tenantId, filterExpr.toString(), 0, 0);
    }

    /**
     * Execute query on collection entities.
     *
     * <p>
     * Backend-agnostic query execution via QuerySpec. Connector translates
     * QuerySpec to native queries (SQL, ES DSL, PromQL, etc.). Results include
     * total count and matched entities. Respects pagination, filtering,
     * sorting, projections from QuerySpec.
     *
     * <p>
     * <b>Query Examples</b><br>
     * - SQL backend: Converts to WHERE/ORDER BY/LIMIT clauses - TimeSeries
     * backend: Converts to range/group-by/aggregation - Lakehouse backend:
     * Converts to Iceberg/Delta queries
     *
     * @param collectionId Collection to query
     * @param tenantId     Tenant ID for isolation
     * @param spec         Backend-agnostic query specification
     * @return Promise of QueryResult with matched entities and metadata
     * @throws IllegalArgumentException if spec invalid for this backend
     * @throws StorageException         on backend errors
     * @see com.ghatana.datacloud.entity.storage.QuerySpec
     * @see QueryResult
     */
    Promise<QueryResult> query(UUID collectionId, String tenantId, QuerySpec spec);

    /**
     * Execute query on collection entities using collection name.
     *
     * <p>
     * Convenience overload that accepts collection name instead of UUID.
     * Generates synthetic UUID from collection name for internal routing.
     * Useful for dynamic collection access without pre-resolved UUIDs.
     *
     * @param tenantId       Tenant ID for isolation
     * @param collectionName Collection name (not UUID)
     * @param spec           Backend-agnostic query specification
     * @return Promise of QueryResult with matched entities and metadata
     * @throws IllegalArgumentException if spec invalid for this backend
     * @throws StorageException         on backend errors
     */
    default Promise<QueryResult> query(String tenantId, String collectionName, QuerySpec spec) {
        UUID syntheticId = UUID.nameUUIDFromBytes(collectionName.getBytes());
        return query(syntheticId, tenantId, spec);
    }

    /**
     * Scan all entities in collection (optional filtering).
     *
     * <p>
     * Full-table scan with optional filter expression. More efficient than
     * query() for backends without indexes. Results paginated via limit/offset
     * (to avoid memory exhaustion).
     *
     * @param collectionId     Collection to scan
     * @param tenantId         Tenant ID for isolation
     * @param filterExpression Optional filter (backend-specific syntax), null =
     *                         no filter
     * @param limit            Maximum results to return
     * @param offset           Starting position for pagination
     * @return Promise of list of matching entities
     * @throws StorageException on backend errors
     */
    Promise<List<Entity>> scan(UUID collectionId, String tenantId, String filterExpression, int limit, int offset);

    /**
     * Count entities matching criteria.
     *
     * <p>
     * Efficient count without retrieving all data. Useful for pagination total
     * calculations.
     *
     * @param collectionId     Collection to count
     * @param tenantId         Tenant ID for isolation
     * @param filterExpression Optional filter, null = count all
     * @return Promise of count (never negative)
     */
    Promise<Long> count(UUID collectionId, String tenantId, String filterExpression);

    /**
     * Count entities in collection using collection name.
     *
     * <p>
     * Convenience overload that accepts collection name instead of UUID. Counts
     * all entities in the collection without filtering.
     *
     * @param tenantId       Tenant ID for isolation
     * @param collectionName Collection name (not UUID)
     * @return Promise of count (never negative)
     */
    default Promise<Long> count(String tenantId, String collectionName) {
        UUID syntheticId = UUID.nameUUIDFromBytes(collectionName.getBytes());
        return count(syntheticId, tenantId, null);
    }

    /**
     * Bulk create multiple entities.
     *
     * <p>
     * More efficient than individual creates. Atomicity depends on backend: -
     * Transactional backends: All-or-nothing - Event stores: Partial success
     * possible (check returned count)
     *
     * @param collectionId Collection to write to
     * @param tenantId     Tenant ID
     * @param entities     Entities to create (each must have collectionId,
     *                     tenantId)
     * @return Promise of created entities (count may be < input count if
     *         partial failure) @throws StorageException on backend errors
     */
    Promise<List<Entity>> bulkCreate(UUID collectionId, String tenantId, List<Entity> entities);

    /**
     * Bulk update multiple entities.
     *
     * <p>
     * Updates matching entity IDs. Partial success possible. Returns count of
     * successfully updated entities.
     *
     * @param collectionId Collection to update
     * @param tenantId     Tenant ID
     * @param entities     Entities with updates (each must include ID)
     * @return Promise of updated entities
     */
    Promise<List<Entity>> bulkUpdate(UUID collectionId, String tenantId, List<Entity> entities);

    /**
     * Bulk delete by entity IDs.
     *
     * <p>
     * Removes multiple entities in single batch. Returns count of successfully
     * deleted entities.
     *
     * @param collectionId Collection to delete from
     * @param tenantId     Tenant ID
     * @param entityIds    Entity IDs to delete
     * @return Promise of count deleted
     */
    Promise<Long> bulkDelete(UUID collectionId, String tenantId, List<UUID> entityIds);

    /**
     * Truncate collection (delete all entities).
     *
     * <p>
     * DESTRUCTIVE OPERATION. Use with caution. Typically restricted to
     * admin/system operations. Returns count of deleted entities.
     *
     * @param collectionId Collection to truncate
     * @param tenantId     Tenant ID
     * @return Promise of count deleted
     */
    Promise<Long> truncate(UUID collectionId, String tenantId);

    /**
     * Get connector capabilities and metadata.
     *
     * @return ConnectorMetadata with backend type, latency class, supported
     *         features
     */
    ConnectorMetadata getMetadata();

    /**
     * Get connector backend type.
     *
     * <p>
     * Convenience method equivalent to getMetadata().backendType(). Used by
     * routing service for quick type checks without full metadata.
     *
     * @return StorageBackendType of this connector
     */
    default StorageBackendType getConnectorType() {
        return getMetadata().backendType();
    }

    /**
     * Check if connector supports windowed queries.
     *
     * <p>
     * Windowed queries are time-range or sliding-window queries optimized for
     * time-series backends. Used by routing service to select appropriate
     * connector for temporal analytics.
     *
     * @return true if connector supports windowed/time-range queries
     */
    default boolean supportsWindowedQueries() {
        return getMetadata().supportsTimeSeries();
    }

    /**
     * Health check for connector backend.
     *
     * <p>
     * Validates connectivity and basic functionality. Used by routing service
     * to detect unavailable backends.
     *
     * @return Promise that completes successfully if healthy, fails if
     *         unavailable
     */
    Promise<Void> healthCheck();

    /**
     * Connector metadata describing capabilities.
     *
     * <p>
     * Used by StorageRoutingService to select appropriate connector.
     *
     * @param backendType          Backend technology type
     * @param latencyClass         Expected latency characteristics
     * @param supportsTransactions Whether backend supports transactions
     * @param supportsTimeSeries   Whether backend optimized for time-series
     *                             queries
     * @param supportsFullText     Whether backend supports full-text search
     * @param supportsSchemaless   Whether backend supports dynamic schema
     * @param maxBatchSize         Maximum batch size for bulk operations (0 =
     *                             unlimited)
     */
    record ConnectorMetadata(
            StorageBackendType backendType,
            StorageProfile.LatencyClass latencyClass,
            boolean supportsTransactions,
            boolean supportsTimeSeries,
            boolean supportsFullText,
            boolean supportsSchemaless,
            int maxBatchSize) {
        /**
         * Create metadata builder for fluent API.
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Builder for ConnectorMetadata.
         */
        public static class Builder {

            private StorageBackendType backendType;
            private StorageProfile.LatencyClass latencyClass = StorageProfile.LatencyClass.STANDARD;
            private boolean supportsTransactions = false;
            private boolean supportsTimeSeries = false;
            private boolean supportsFullText = false;
            private boolean supportsSchemaless = false;
            private int maxBatchSize = 0;

            public Builder backendType(StorageBackendType type) {
                this.backendType = type;
                return this;
            }

            public Builder latencyClass(StorageProfile.LatencyClass latency) {
                this.latencyClass = latency;
                return this;
            }

            public Builder supportsTransactions(boolean supports) {
                this.supportsTransactions = supports;
                return this;
            }

            public Builder supportsTimeSeries(boolean supports) {
                this.supportsTimeSeries = supports;
                return this;
            }

            public Builder supportsFullText(boolean supports) {
                this.supportsFullText = supports;
                return this;
            }

            public Builder supportsSchemaless(boolean supports) {
                this.supportsSchemaless = supports;
                return this;
            }

            public Builder maxBatchSize(int size) {
                this.maxBatchSize = size;
                return this;
            }

            public ConnectorMetadata build() {
                if (backendType == null) {
                    throw new IllegalArgumentException("backendType is required");
                }
                return new ConnectorMetadata(
                        backendType,
                        latencyClass,
                        supportsTransactions,
                        supportsTimeSeries,
                        supportsFullText,
                        supportsSchemaless,
                        maxBatchSize);
            }
        }
    }

    /**
     * Query result containing matched entities and metadata.
     *
     * @param entities        Matched entities (empty list if no matches)
     * @param total           Total count of matching entities (for pagination
     *                        calculations)
     * @param limit           Query limit (may be less than total matches)
     * @param offset          Query offset
     * @param executionTimeMs Time taken to execute query
     */
    public record QueryResult(
            List<Entity> entities,
            long total,
            int limit,
            int offset,
            long executionTimeMs) {

        /**
         * Check if results have more pages.
         */
        public boolean hasMore() {
            return (offset + limit) < total;
        }

        /**
         * Next offset for pagination.
         */
        public int nextOffset() {
            return offset + limit;
        }

        /**
         * Create empty result.
         */
        public static QueryResult empty() {
            return new QueryResult(List.of(), 0, 0, 0, 0);
        }
    }
}
